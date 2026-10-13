// Run after build.bat: node scripts/test-json-export.cjs
// Uses the bundled official Spine 4.3.5 TypeScript runtime, without a browser or npm dependencies.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { spawnSync } = require('node:child_process');

const project = path.resolve(__dirname, '..');
const output = path.join(project, 'build', 'skin-json-check');
const context = {};
vm.runInNewContext(fs.readFileSync(path.join(project, 'assets/spine-player/4.3.5/spine-player.min.js'), 'utf8'), context);
const spine = context.spine;
// Supply geometry-only regions: the TS reader computes mesh UVs during loading.
function withRegions(attachment) {
  const region = Object.assign(new spine.TextureRegion(), {
    u2: 1, v2: 1, width: 16, height: 16, originalWidth: 16, originalHeight: 16,
  });
  attachment.sequence.regions.fill(region);
  return attachment;
}
const loader = {
  newRegionAttachment: (_skin, _placeholder, name, _path, sequence) => withRegions(new spine.RegionAttachment(name, sequence)),
  newMeshAttachment: (_skin, _placeholder, name, _path, sequence) => withRegions(new spine.MeshAttachment(name, sequence)),
  newBoundingBoxAttachment: (_skin, _placeholder, name) => new spine.BoundingBoxAttachment(name),
  newPathAttachment: (_skin, _placeholder, name) => new spine.PathAttachment(name),
  newPointAttachment: (_skin, _placeholder, name) => new spine.PointAttachment(name),
  newClippingAttachment: (_skin, _placeholder, name) => new spine.ClippingAttachment(name),
};
const read = root => new spine.SkeletonJson(loader).readSkeletonData(root);
const names = values => Array.from(values, value => value.name);
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);

function verify(root) {
  assert(Array.isArray(root.skins), 'skins must be a JSON array; object-shaped skins are silently skipped by spine-ts');
  const skinNames = root.skins.map(skin => skin.name);
  assert.equal(new Set(skinNames).size, skinNames.length, 'skin names must be unique');
  for (const skin of root.skins) {
    assert(isObject(skin) && typeof skin.name === 'string' && skin.name.length > 0, 'each skin needs an explicit name');
    assert(isObject(skin.attachments), 'skin attachments must remain a slot-keyed object');
  }
  const data = read(root);
  assert.deepEqual(names(data.skins), skinNames, 'all skins and their order survive the official TS reader');
  assert.equal(data.bones.length, root.bones.length, 'bone count');
  assert.equal(data.slots.length, root.slots.length, 'slot count');
  assert.deepEqual(names(data.constraints), (root.constraints || []).map(value => value.name), 'constraints');
  assert.deepEqual(names(data.animations), Object.keys(root.animations), 'animations');
  assert.equal(data.defaultSkin?.name, skinNames.includes('default') ? 'default' : undefined, 'default skin selection');
  let count = 0;
  for (const skin of root.skins) {
    const actual = data.findSkin(skin.name);
    const entries = Object.entries(skin.attachments);
    const expectedCount = entries.reduce((total, [, attachments]) => total + Object.keys(attachments).length, 0);
    assert.equal(actual.getAttachments().length, expectedCount, `attachment count: ${skin.name}`);
    assert.deepEqual(names(actual.bones), skin.bones || [], `skin bones: ${skin.name}`);
    const constraints = ['ik', 'transform', 'path', 'physics', 'slider'].flatMap(type => skin[type] || []);
    assert.deepEqual(names(actual.constraints), constraints, `skin constraints: ${skin.name}`);
    for (const [slotName, attachments] of entries) {
      const slot = data.findSlot(slotName);
      assert(slot, `slot exists: ${slotName}`);
      for (const [placeholder, expected] of Object.entries(attachments)) {
        const attachment = actual.getAttachment(slot.index, placeholder);
        assert(attachment, `attachment exists: ${skin.name}/${slotName}/${placeholder}`);
        assert.equal(attachment.name, expected.name || placeholder, 'attachment name');
        if (expected.type === 'linkedmesh') {
          const source = data.findSkin(expected.skin || 'default');
          const sourceSlot = data.findSlot(expected.slot || slotName);
          assert.equal(attachment.getSourceMesh(), source.getAttachment(sourceSlot.index, expected.source), 'linked mesh source');
        }
        count++;
      }
    }
  }
  return { data, count };
}

const result = spawnSync('java', ['-cp', ['bin', 'lib/*'].join(path.delimiter),
  'com.openspine.io.SkinJsonExportTest', output], { cwd: project, encoding: 'utf8' });
if (result.error) throw result.error;
assert.equal(result.status, 0, `Build with build.bat first. Java export checks failed:\n${result.stdout}${result.stderr}`);
process.stdout.write(result.stdout);

for (const name of ['no-skins', 'empty-default', 'single-default', 'single-named', 'multi-skin', 'legacy-example']) {
  const root = JSON.parse(fs.readFileSync(path.join(output, `${name}.json`), 'utf8'));
  const { data, count } = verify(root);
  if (name === 'multi-skin') {
    assert.equal(count, 4, 'all four fixture attachments survive');
    assert.equal(data.findSkin('empty').getAttachments().length, 0, 'empty named skin survives');
    const animation = data.findAnimation('move');
    assert.equal(animation.duration, 1, 'animation duration');
    assert.equal(animation.timelines.length, 3, 'bone, deform and sequence timelines survive');
    const deform = animation.timelines.find(timeline => timeline instanceof spine.DeformTimeline);
    const sequence = animation.timelines.find(timeline => timeline instanceof spine.SequenceTimeline);
    assert(deform && sequence, 'attachment animation timelines retain their types');
    assert.equal(deform.attachment, data.defaultSkin.getAttachment(0, 'quad'), 'deform attachment reference');
    assert.equal(sequence.attachment, data.defaultSkin.getAttachment(0, 'sprite'), 'sequence attachment reference');
    assert.deepEqual(Array.from(deform.frames), [0, 1], 'deform key times');
    assert.equal(data.defaultSkin.getAttachment(0, 'sprite').sequence.regions.length, 2, 'sequence frame count');
  }
  if (name === 'legacy-example') {
    assert(count > 0, 'legacy fixture includes attachments');
    // Recreate the original bug. With no attachment animation to trigger another error,
    // the actual official TS reader silently returns zero skins and attachments.
    const malformed = { ...root, skins: Object.fromEntries(root.skins.map(skin => [skin.name, skin])) };
    malformed.animations = {};
    assert.throws(() => verify(malformed), /skins must be a JSON array/, 'structural guard rejects the old export');
    const silentlyBroken = read(malformed);
    assert.equal(silentlyBroken.skins.length, 0, 'the negative control reproduces the runtime blind spot');
    assert.equal(silentlyBroken.defaultSkin, null, 'the old format loses the default skin');
    console.log('NEGATIVE CONTROL: object-shaped skins are rejected by the guard and silently skipped by spine-ts');
  }
  console.log(`${name}: ${data.skins.length} skins, ${count} attachments, ${data.animations.length} animations OK`);
}
console.log('ALL SPINE-TS JSON EXPORT TESTS PASSED');
