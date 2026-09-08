[CmdletBinding()]
param(
	[string] $Version = "0.2.91",
	[string] $OutputDirectory = "",
	[string] $WorkDirectory = "",
    [switch] $SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$buildScript = Join-Path $projectRoot "build.bat"
$sourceDir = Join-Path $projectRoot "src"
$classesDir = Join-Path $projectRoot "bin"
$libraryDir = Join-Path $projectRoot "lib"
$applicationIcon = Join-Path $projectRoot "assets\icons\openspine.ico"
$releaseWorkDir = if ([string]::IsNullOrWhiteSpace($WorkDirectory)) {
	Join-Path $projectRoot "build\release"
} else {
	[IO.Path]::GetFullPath((Join-Path $projectRoot $WorkDirectory))
}
$applicationClassesDir = Join-Path $releaseWorkDir "classes"
$packageInputDir = Join-Path $releaseWorkDir "input"
$mcpLauncherProperties = Join-Path $releaseWorkDir "OpenSpineMCP.properties"
$previewLauncherProperties = Join-Path $releaseWorkDir "OpenSpinePreview.properties"
$publishDir = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
	Join-Path $projectRoot "publish"
} else {
	[IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
}
$appImageDir = Join-Path $publishDir "OpenSpine"
$archivePath = Join-Path $publishDir "OpenSpine-$Version-win64.zip"
$checksumPath = "$archivePath.sha256"

function Assert-Tool([string] $Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required JDK tool was not found on PATH: $Name"
    }
}

function Remove-ManagedPath([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $resolvedRoot = [IO.Path]::GetFullPath($projectRoot).TrimEnd('\')
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    if (-not $resolvedPath.StartsWith("$resolvedRoot\", [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a path outside the project: $resolvedPath"
    }
    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

Assert-Tool "java"
Assert-Tool "jar"
Assert-Tool "jpackage"
if (-not (Test-Path -LiteralPath $applicationIcon -PathType Leaf)) {
    throw "Application icon was not found: $applicationIcon"
}

Write-Host "[1/5] Compiling sources..."
& $buildScript
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed with exit code $LASTEXITCODE."
}
$buildInfoPath = Join-Path $classesDir 'openspine-build.properties'
if (-not (Test-Path -LiteralPath $buildInfoPath -PathType Leaf)) {
    throw 'Compilation did not produce build provenance.'
}
$buildInfo = ConvertFrom-StringData (Get-Content -LiteralPath $buildInfoPath -Raw)

$testClasses = Get-ChildItem -LiteralPath $sourceDir -Recurse -Filter "*Test.java" -File |
	ForEach-Object {
		$relativePath = $_.FullName.Substring($sourceDir.Length + 1)
		$relativePath.Replace('\', '.').Substring(0, $relativePath.Length - ".java".Length)
	} |
	Sort-Object -Unique
if ($testClasses.Count -eq 0) {
	throw "No regression tests were found under $sourceDir."
}

if (-not $SkipTests) {
	Write-Host "[2/5] Running headless regression tests..."
	foreach ($testClass in $testClasses) {
		& java -cp "$classesDir;$libraryDir\*" $testClass
		if ($LASTEXITCODE -ne 0) {
			throw "$testClass failed with exit code $LASTEXITCODE."
		}
	}
} else {
	Write-Host "[2/5] Regression tests skipped."
}

Write-Host "[3/5] Preparing application JARs..."
Remove-ManagedPath $releaseWorkDir
New-Item -ItemType Directory -Path $applicationClassesDir -Force | Out-Null
New-Item -ItemType Directory -Path $packageInputDir -Force | Out-Null

Get-ChildItem -LiteralPath $classesDir -Force |
	Copy-Item -Destination $applicationClassesDir -Recurse -Force
Get-ChildItem -LiteralPath $applicationClassesDir -Recurse -File |
	Where-Object { $_.Name -cmatch 'Test(?:\$.*)?\.class$' } |
	Remove-Item -Force

$mainJar = Join-Path $packageInputDir "OpenSpine.jar"
& jar --create --file $mainJar --main-class com.openspine.OpenSpine -C $applicationClassesDir .
if ($LASTEXITCODE -ne 0) {
    throw "Unable to create OpenSpine.jar."
}
$packagedTests = @(& jar --list --file $mainJar | Where-Object { $_ -cmatch 'Test(?:\$.*)?\.class$' })
if ($LASTEXITCODE -ne 0) {
	throw "Unable to inspect OpenSpine.jar."
}
if ($packagedTests.Count -ne 0) {
	throw "OpenSpine.jar contains regression-test classes: $($packagedTests -join ', ')"
}

Get-ChildItem -LiteralPath $libraryDir -Filter "*.jar" -File | Copy-Item -Destination $packageInputDir
Get-ChildItem -LiteralPath $libraryDir -File |
	Where-Object { $_.Name -like '*.NOTICE.md' -or $_.Name -like '*LICENSE*.txt' } |
	Copy-Item -Destination $packageInputDir

@"
main-jar=OpenSpine.jar
main-class=com.openspine.mcp.McpLauncher
java-options=--add-opens=java.base/java.lang=ALL-UNNAMED
win-console=true
"@ | Set-Content -LiteralPath $mcpLauncherProperties -Encoding Ascii

@"
main-jar=OpenSpine.jar
main-class=com.openspine.mcp.McpRenderWorker
java-options=--add-opens=java.base/java.lang=ALL-UNNAMED -Xmx512m
win-console=false
"@ | Set-Content -LiteralPath $previewLauncherProperties -Encoding Ascii

Write-Host "[4/5] Creating the self-contained Windows application..."
New-Item -ItemType Directory -Path $publishDir -Force | Out-Null
Remove-ManagedPath $appImageDir
Remove-ManagedPath $archivePath
Remove-ManagedPath $checksumPath

& jpackage `
    --type app-image `
    --name OpenSpine `
    --app-version $Version `
    --vendor OpenSpine `
    --description "OpenSpine skeleton animation editor" `
    --icon $applicationIcon `
    --input $packageInputDir `
    --dest $publishDir `
    --main-jar "OpenSpine.jar" `
    --main-class "com.openspine.OpenSpine" `
    --add-launcher "OpenSpineMCP=$mcpLauncherProperties" `
    --add-launcher "OpenSpinePreview=$previewLauncherProperties" `
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

$releaseNotes = @"
OpenSpine $Version

Build UTC: $($buildInfo.built_at)
Source/input SHA-256: $($buildInfo.source_sha256)
Git: $($buildInfo.git_commit) ($($buildInfo.worktree_state))

Run OpenSpine.exe to start the editor. A separate Java installation is not required.

OpenSpineMCP.exe starts the headless stdio MCP server and requires --root <directory>
to define its project access boundary. The server supports modern MCP 2026-07-28
discovery and legacy initialize clients, and exposes bounded project discovery,
inspection, full or chunked JSON reads, validation, atomic protected writes, PNG render
previews, and opt-in editor screenshots. OpenSpinePreview.exe is the hidden rendering
helper and must remain beside OpenSpineMCP.exe. Rendered textures must stay under --root.
To allow screenshots, start OpenSpine.exe --mcp-capture-root <the same directory> and
open a project from that directory. Screenshots include the editor client area (possibly
unsaved content), never the desktop. Image tools return inline MCP image/png content.
MCP validates mesh index ranges, UV/vertex sizes, hull, weighted bone references and
finite geometry before accepting projects. Non-normalized weights return warnings.
Empty animation timelines are rejected with their JSON paths; omit unused timelines.
Legal empty values inside keyframes are preserved. Bounded exception cause summaries
retain animation names and the underlying runtime error in MCP responses.
valid=true means structure_valid=true, not that textures or every animation were checked;
textures_checked and render_checked explicitly describe the validation scope.
MCP serverInfo includes build provenance, and --version includes the source fingerprint.

Spine 4.3 binary skeleton export is now available from Save As and preserves the current
format when saving an opened .skel file. The writer covers all supported attachments,
weighted vertices, linked meshes, constraints, animations, Deform and Sequence data,
editor metadata, Base64 or decimal skeleton hashes, and multilingual strings. Binary
files are written atomically and regression-tested by reloading them with the matching
official SkeletonBinary runtime before release packaging.

Spine 4.3 Path setup constraints can now be created, edited, activated for the current
skin, and safely deleted from the timeline toolbar. The editor covers ordered constrained
bones, target slot, Position/Spacing/Rotate modes, rotation offset, position, spacing,
three setup mixes, skin-required state, and current-skin activation. Updates replace
immutable-name setup data at the same unified index, preserving loaded Path animation
targets and all skin references atomically. Invalid references, duplicate bones, non-finite
values, and target-slot dependency cycles are rejected before data changes. Position,
spacing, and three-channel mix timelines can be added, edited, moved, copied, pasted,
curved, removed, undone, and redone in the visual timeline.

Viewport interaction now selects visible Region and Mesh attachments across their full
rendered geometry, preferring the topmost draw-order entry and falling back to bone-line
picking. Wheel zoom reliably follows the mouse over the viewport and remains anchored at
the cursor. Dynamic animation, skin, slot, attachment, and atlas-region dropdowns now keep
their popup lists synchronized. Pending skin-required setup edits survive render refreshes,
timeline drags stop safely if filtering or edits remove their track, animation deletion
preserves configured transition mixes, and failed undo/redo actions no longer escape into
the render loop. Duplicate draw-order timelines are rejected before export.

Spine 4.3 Physics constraints can now be created, edited, activated for the current
skin, and safely deleted from the timeline toolbar. The editor covers the target bone,
five transform influences, Scale Y behavior, movement limit, integer simulation FPS,
inertia, strength, damping, mass, wind, gravity, mix, and every per-property global flag.
Updates replace immutable-name setup data at the same unified index, preserving loaded
animation targets and all skin references atomically. Path and Physics setup data, skin
activation, all Path timelines, all seven Physics value timelines, reset timelines, and
global Physics timelines now round-trip through the official Spine 4.3 JSON loader.
Malformed references, duplicate properties, empty or unordered keys, non-finite values,
invalid mass, and simulation steps that cannot be represented by integer FPS are rejected
before the destination is touched. Local and global Physics value and reset timelines can
be added, edited, moved, copied, pasted, curved where applicable, removed, undone, and redone.

Spine 4.3 Transform constraints are now supported end to end. Setup constraints can
be created, edited, activated for the current skin, and safely deleted from the
timeline toolbar. The editor covers constrained and source bones, local source and
target modes, additive and clamp modes, six offsets, and six unbounded setup mixes.
Loaded advanced from/to property mappings are preserved exactly when ordinary setup
fields are edited. Standard same-channel mappings are created for new constraints.
Arbitrary mappings, setup data, skin activation, and six-channel animation timelines
round-trip through the official JSON loader, including linear, stepped, and per-channel
Bezier curves. Transform tracks can sample the live applied pose, edit, drag, delete,
copy/paste, navigate, filter by affected bone, and undo or redo complete keys and curves.
Invalid dependency cycles, references, duplicate mappings, non-finite values, duplicate
timelines, and destructive index shifts are rejected before data is changed or saved.

Spine 4.3 attachment data can now be saved without flattening projects to region-only
content. Region, mesh, linked mesh, bounding box, path, point, and clipping attachments
round-trip through the official JSON loader, including custom attachment names, mesh
topology, hulls and edges, weighted and unweighted vertices, clipping options, and
Region/Mesh sequences. Existing weighted and unweighted Deform timelines preserve sparse
vertex data plus linear, stepped, and Bezier interpolation. Region and mesh Sequence
timelines preserve mode, index, and delay. Cross-skin and cross-slot linked meshes preserve
their source and timeline inheritance. Invalid or unrepresentable attachment and timeline
data is rejected before the destination file is touched. Region and Mesh attachments can be
created from atlas regions, Region attachments can be converted to Mesh, and independent
Mesh topology, UVs, hulls, edges, weighted or unweighted vertices can be validated and
edited. Shift-click selects multiple setup or Deform vertices for one group drag and one
undo step, including per-vertex weighted transform conversion. Bounding box, Path, Point,
and Clipping attachments can be created and edited with type-specific geometry and runtime
options, then selected from their viewport overlays. Deform tracks can create, edit, drag,
copy/paste, delete, and interpolate vertex offset keys. Region and Mesh Sequence
tracks can create, edit, drag, copy/paste, and delete discrete mode, start-index, and delay keys.
Region and Mesh attachments can configure atlas-backed Sequence base paths, frame counts,
start numbers, zero padding, and setup indices with per-frame atlas validation.
Attachment and skin removal now refuse to orphan Deform, Sequence, or linked-mesh source
references. The single-region action cannot overwrite mesh or multi-frame sequence data,
and unsafe non-mesh skin copies are blocked until their inherited timelines can be cloned.

IK setup constraints can now be created, edited, activated for the current skin,
and safely deleted from the timeline toolbar. The modal editor covers one-bone
and direct parent-child two-bone chains, target selection, setup mix and softness,
bend direction, compression, stretch, Scale Y behavior, skin-required state, and
current-skin activation. Every submission is validated and applied atomically,
then the runtime skeleton and dependent panels are rebuilt. Deletion is blocked
while skin or animation references remain or a later unified constraint index
would be shifted. Semantic draft comparison avoids false changes from whitespace
or equivalent numeric notation.

Spine 4.3 IK constraints are now supported end to end. Existing IK setup data, skin
activation references, and animation timelines round-trip through standard Spine JSON.
The timeline exposes one track per IK constraint and can sample the live applied pose,
edit mix and softness, change bend direction, compression, and stretch, drag or delete
keys, copy/paste complete values and curves, navigate adjacent keys, and undo or redo
every operation. Bezier interpolation is preserved only for the two continuous IK
channels, while the three discrete options remain exact. Selected-bone filtering keeps
IK tracks whose target or constrained-bone list contains the selected bone.

Spine 4.3 global draw-order timelines are now editable end to end. The timeline
can capture the current runtime slot order, rearrange the complete slot list,
restore Setup order, edit or drag key times, delete keys, copy/paste, navigate
adjacent keys, and undo or redo every change. Animation duplication preserves
independent permutations. Export writes standard drawOrder offsets, validates
every permutation and frame time, and reloads the result with the official runtime.

Spine 4.3 split RGB and alpha slot timelines are now fully editable. Imported tracks
support exact value editing, insertion, dragging, deletion, copy/paste, Bezier curves,
animation duplication, and undo/redo. RGB can coexist with alpha, RGB2 can coexist
with alpha, and conflicting RGBA or RGBA2 combinations are blocked before mutation.
The exporter now writes standard rgb and alpha JSON tracks and verifies times, values,
stepped interpolation, and Bezier samples by reloading through the official runtime.
Alpha-only slots with setup dark tint retain the RGB2-compatible primary track.

The generated desktop font now covers the GB2312 common Simplified Chinese set,
including every non-ASCII character currently used by the UI and common user bone,
slot, skin, and animation names. Atlas upload uses bulk RGBA conversion, failed font
creation is logged, and partially created native resources are released safely.
Open and Save As dialogs now run entirely on Swing's event dispatch thread and
fall back to an in-editor error instead of leaking dialog failures through input
callbacks. The editor remembers separate recent open and save directories across
restarts; startup files, drag-and-drop, and successful saves update those locations.
Shared scroll panes now provide stable 10-pixel scrollbar hit areas and 28-pixel
minimum knobs instead of inheriting one-pixel dimensions from generated textures.
Tree disclosure controls are easier to target, and long status or error messages
truncate within the status bar instead of forcing the workspace beyond the window.
Viewport wheel zoom now follows the vertical scroll magnitude and keeps the world
point below the cursor fixed. Horizontal-only or invalid scroll input no longer zooms,
unsupported mouse buttons pass through, and unchanged selection or mouse release no
longer emits redundant editor callbacks. Framing rejects non-finite skeleton bounds.
Playback controls now include direct frame-number navigation. Enter validates the
frame against the active animation and project FPS, pauses playback, exits pose mode,
and synchronizes the slider and preview. Invalid or out-of-range input stays focused,
Escape restores the current frame, and non-frame-aligned animation ends remain reachable.
Leaving the frame field without submitting now restores the actual playhead frame instead
of leaving an unapplied value visible. Loading another document also replaces any focused
frame draft immediately, so playback controls cannot display state from the previous file.
Removing an attachment or an entire skin now checks every remaining skin before clearing
the slot setup attachment name. Placeholders owned only by an inactive skin remain usable
when that skin is selected, while names with no remaining owner are still removed cleanly.
Accent and active-tool buttons now use a muted background and text color when disabled,
so unavailable playback, inspector, timeline, and dialog actions no longer appear clickable.
The bone hierarchy now preserves manual expansion state across filtering, undo, and
structural panel rebuilds. Filter matches remain fully expanded, newly added branches
start open, and selecting a hidden descendant from the viewport reveals its ancestor path.
Bone filtering now marks matching ancestor paths in one pass instead of recursively
rescanning every subtree, keeping search responsive on deep or high-count skeletons.
Bone filter keyboard navigation now matches the timeline search: Enter focuses the
tree, Down advances into its visible results, and Escape first clears the query then
returns focus to the tree. Selecting a bone from the viewport expands and scrolls its
tree node into view after layout. Tree rebuilds connect parent nodes through a single
identity index instead of repeatedly searching every created node.
Timeline search and track filters now retain a still-valid key selection while its
track is hidden, disable inspector edits in that state, and restore the selection when
the track becomes visible again. Rebuilds index slots and animation timelines once,
and visible tracks reuse those resolved timelines while drawing and dragging instead
of repeatedly scanning the complete skeleton and animation on every rendered frame.
Timeline mouse input now follows Spine conventions: left-click selects a key, dragging
from empty space draws a cross-track marquee, Ctrl/Shift extends or toggles the selection,
and dragging any selected key moves the complete group as one undoable edit. Right-button
drag pans both scroll axes and no longer deletes data. Delete removes the complete key
selection, while double-clicking empty track space remains the explicit insertion gesture.
Timeline text editing now returns cleanly to keyboard navigation. Successful Enter
commits for project FPS and keyframe time, value, or color focus the canvas; invalid
commits stay focused for correction, while Escape restores model values. Track
search supports Enter or Down to focus the canvas and a two-step Escape clear/exit.
Slot and skin name fields plus slot and region property fields now share predictable
keyboard behavior: Enter only leaves after a successful atomic apply or rename,
while Escape restores current model values. Invalid slot light and dark colors stay
focused with an exact validation message for correction.
Slot and region Apply actions now remain disabled until their normalized drafts differ
from the loaded setup values. Equivalent color notation, scientific numeric notation,
and disabled dark-color text no longer create false changes; invalid edited drafts stay
submittable for precise validation. Slot and skin Rename actions likewise require a
nonblank trimmed name that differs from the current model name.
Timeline keyframe Apply now follows the same semantic draft rules for time, numeric values,
RGBA/RGB/alpha/RGB2 colors, attachment or event selections, and linear or stepped curves. Equivalent
formatting no longer creates a false pending edit, while invalid changed drafts remain
submittable for precise validation. Enter also respects disabled Apply actions.
Slot setup colors, two-color state, blend mode, visibility, and region attachment transforms
now create reversible document-history entries instead of clearing the complete undo stack.
Undo and redo restore the full validated property group and rebuild the runtime skeleton;
structural slot, attachment, and skin edits still clear stale history safely. Project FPS
Enter now skips equivalent valid drafts without bypassing validation for malformed input.
Bone property fields now keep invalid input focused after Enter and report a clear
validation error instead of silently discarding it. Leaving an invalid field rolls
back its display, Escape cancels the edit, and successful commits no longer trigger
duplicate pose refresh callbacks.
The bone inspector now provides a Set as Setup command whenever the selected bone's
temporary pose differs from its exported setup transform. The complete local pose is
validated, written to SkeletonData, exported to Spine JSON, and tracked by undo/redo
without making the preview jump. Runtime skeleton replacements also rebind the bone
tree only when necessary, preventing stale selections after setup or attachment edits.
Bone length is now an editable Setup draft with a precise numeric field and a compact
slider. Pose and length commit atomically through Set as Setup, share one undo entry,
and export together; invalid or negative values cannot partially modify the document.
The control follows pose-mode changes immediately, Escape restores the model value,
and failed button submission returns focus to the length field for correction.
The bone inspector now exposes all five Spine 4.3 parent-transform inherit modes and
the skin-required flag. Inherit changes preview immediately and participate in pose
undo/redo; pending length and skin drafts survive that history refresh. Set as Setup
commits pose, length, inherit mode, and skin requirement atomically and exports them
using Spine's inherit and skin fields. Invalid setup data leaves every property unchanged.
Bone transform inheritance can now be animated on a dedicated discrete timeline.
All five Spine 4.3 modes support key creation, exact time and mode editing, dragging,
deletion, copy/paste, adjacent-key navigation, animation duplication, and undo/redo.
Inheritance keys export through Spine's standard bone inherit timeline and are verified
by reloading the generated JSON with the official 4.3 runtime.
Document-level save, open, drop, example, and native close actions now finish a
focused workspace text edit before saving or checking for unsaved changes. Just-typed
bone values are therefore included in saves and close prompts, while modal form input
remains isolated until its own dialog action is submitted.
Adding an event key now respects the intended event definition. Projects with one
definition retain double-click insertion; projects with multiple definitions open a
compact keyboard-friendly chooser at the snapped key time. The chooser remembers
the most recently selected, created, or renamed definition, and cancellation leaves
the animation and undo history untouched.
Same-time event keys are now distributed vertically within the event track instead
of being painted on top of each other. Two-dimensional nearest-marker hit testing
makes each visible event directly selectable for editing, group dragging, or Delete-key
deletion, while the key inspector reports the exact position within its time group.
Event definitions and animation event keys can now be edited directly from the
timeline workspace. The global event track supports insertion, exact-time and
definition edits, payload editing, movement, deletion, copy/paste, adjacent-key
navigation, and shared undo/redo. A definition manager creates, renames, updates,
and safely deletes definitions while preserving all references across animations.
Multiline strings use Enter for a new line and Ctrl+Enter to submit. Same-time
events retain stable ordering and the selected event remains the exact mouse target.
Spine 4.3 event definitions and animation event timelines can now be saved without
data loss. Integer, float, string, audio path, volume, and balance values preserve
their setup defaults and per-key overrides; multiple events at the same time retain
their order. Export validation rejects dangling definitions, invalid times, null
payloads, non-finite values, and malformed duplicate event tracks before writing.
Bone hierarchy context menus can now copy an entire subtree and paste it below any
selected bone. Setup transforms and bone display metadata are snapshotted independently,
all pasted names are resolved atomically, and existing bone indices stay unchanged.
Recursive subtree traversal no longer uses nested libGDX Array iterators, fixing a crash
when validating or deleting an unreferenced tail hierarchy deeper than one level.
Bezier interpolation can now be created and edited from the selected-key inspector.
The modal curve editor provides normalized X1/Y1/X2/Y2 fields, draggable handles,
live overshoot-aware visualization, and ease-in, smooth, and ease-out presets.
Edits apply consistently to every value channel and remain one undoable operation.
The skeleton hierarchy and inspector now have draggable horizontal dividers, so
both sidebars can be resized without squeezing the viewport through fixed widths.
Their proportions persist across restarts, and invalid saved values fall back to
bounded defaults that keep every workspace pane usable.
Individual Bezier keys can now be copied and pasted with their complete per-channel
curve data. Clipboard curves are remapped to the pasted key's time and value endpoints,
remain one undoable timeline operation, and preserve exact samples through undo/redo.
RGB2 and RGBA2 clipboard types are checked before paste to prevent channel misalignment.
Bezier curves on supported bone, RGBA, RGB2, and RGBA2 timelines can now be
saved to Spine 4.3 JSON. The exporter reconstructs each channel's cubic handles
from the runtime samples, and export regression tests reload the JSON through the
official SkeletonJson reader and compare every internal curve sample.
Bezier bone, RGBA, RGB2, and RGBA2 tracks now support key insertion, deletion,
movement, value editing, paste destinations, and Record Pose without discarding
unrelated curve samples. Curves follow their outgoing key and are remapped to new
endpoints during timeline rebuilds. The key inspector remains editable on Bezier
tracks; leaving interpolation unselected preserves Bezier, while explicitly choosing
linear or stepped converts only that outgoing segment.
Workspace preferences now persist the timeline height and zoom, track filter,
active inspector tab, and viewport grid and bone visibility across restarts.
Invalid saved layout values are clamped to usable ranges, preference write failures
cannot interrupt editor resource disposal, and the timeline splitter is easier to grab.
Slot RGB2 and RGBA2 two color timelines can now be created, previewed, moved,
edited with separate light and dark hexadecimal fields, copied, undone, and exported.
Imported RGB2 tracks preserve their type during key editing and animation duplication.
Two color keys support linear, stepped, and imported Bezier interpolation and render both colors on the timeline.
Setup two color tinting cannot be disabled while an animation still references it;
export also rejects missing setup dark colors or invalid timeline target indices.
Timeline tracks can be filtered by selected bone, keyed tracks, or name. Mouse
key placement and dragging snap to the skeleton project's FPS.
Playback can be paused and stepped backward or forward by the project FPS, with
the current frame number shown beside the time slider.
Looping animations can now be scrubbed to their exact final frame for editing,
and pausing after multiple loops preserves the currently displayed cycle time.
Switching animations now applies the new first frame immediately while preserving
the current play or pause state. Playback speed also resets with a loaded document.
Programmatic playback-panel refreshes no longer fire the scrub listener, so animation,
selection, skin, and command-state synchronization cannot pause playback accidentally.
Region attachments now expose editable RGBA tint with the same hexadecimal field and
live swatch used by slot colors. Tint and transform values are validated and committed
together, preserved by JSON export, and unchanged submissions remain clean no-ops.
Slot setup properties now expose optional two color tinting with an RGB dark-color field
and live swatch. Light color, dark color, blend mode, and visibility commit atomically;
enabling or disabling two color tinting rebuilds the runtime slot immediately.
Setup dark colors are exported in Spine's six-digit RGB format. Invalid setup or region
colors and missing slot blend modes now stop export with a precise error instead of being
silently quantized or causing a null-pointer failure.
Region-attachment transforms now validate all seven numeric fields before writing any
value. Pressing Enter applies the complete edit, while invalid text remains visible with
a precise error instead of silently restoring one field and committing the others.
Text fields now use a neutral dark background, visible focus and selection states, and
consistent disabled styling. Select boxes and sliders also expose hover and disabled states.
Reapplying unchanged bone, attachment, or color keyframe values no longer reports a
timeline change or rebuilds dependent panels. Record Pose follows the same no-op rule.
Timeline numeric fields now reject NaN and infinity consistently, and attempting to
toggle interpolation on a terminal key reports that no outgoing curve exists.
Applying unchanged region-attachment transforms is now a clean no-op, while invalid
numeric values and attachments outside the current document are rejected before mutation.
Reapplying the same attachment name and atlas region is also a clean no-op and no longer
invalidates edit history. Renaming or duplicating the active skin now reapplies paused
slot timelines at the current playhead instead of leaving slots in their setup pose.
Bone and region-attachment numeric fields now preserve any finite Spine value outside
their convenience slider ranges; synchronizing a bounded slider cannot overwrite the
exact typed value. Non-positive attachment dimensions now show a precise validation error.
Submitting an unchanged bone property no longer pauses animation, enters pose mode, or
increments the document revision. Pose history now records exact finite float changes,
including one-ULP edits that were previously applied without an undo entry.
Clicking a bone with a transform tool now selects it without pausing playback; pose mode
starts only after a real drag changes the pose. Reset Pose uses the same guarded edit path,
so a reset during playback remains visible instead of being overwritten on the next frame.
Switching skins while an animation is paused now reapplies slot attachment and color
timelines at the current time instead of showing setup-state slots at a stale playhead.
Changing a slot's setup attachment likewise preserves any attachment timeline driving
the paused preview, and reselecting the unchanged setup attachment is a clean no-op.
Playback controls are arranged as a compact transport row above a full-width
scrub row so the timeline remains precise at the minimum supported window size.
Project FPS can be edited from the timeline toolbar and is validated, preserved
in JSON export, and applied immediately to playback and timeline snapping.
Selecting or dragging a key, clicking the ruler, or scrubbing playback now
pauses at the exact editor time and keeps the viewport and playback head synced.
Keyframe creation, editing, deletion, paste, curve changes, and movement now
participate in shared undo/redo history; a continuous drag is one history step.
Timeline changes and timeline undo/redo now immediately reapply the active pose at
the current playhead, so deleted or restored keys cannot leave a stale viewport.
Record Pose (or K) keys the selected bone's rotation, translation, scale, and
shear at the current snapped time as one undoable action. Existing single-axis
tracks are preserved without creating overlapping combined tracks.
Duplicating an animation preserves independent timeline copies, including exact
linear, stepped, and Bezier curve data for supported editable tracks.
Frame All now fits both bone geometry and visible region or mesh attachments.
Text fields retain their own undo/redo handling; document redo also supports
Ctrl+Shift+Z when focus is outside an input field.
Save and Save As now follow standard desktop behavior: Ctrl+S writes directly
to an associated JSON file, while Ctrl+Shift+S always opens Save As. JSON input
keeps its source association; binary and built-in documents request a JSON path.
Validated JSON is written beside the destination and atomically replaces the
previous file when supported, preventing partial files after failed saves.
Unsaved-document prompts now offer Save, Don't Save, and Cancel before opening,
dropping, switching examples, or closing. Open runs after that decision, and the
window title shows the active document name plus its unsaved marker.
Document undo/redo now tracks the exact saved history position. Returning to a
savepoint clears the unsaved marker, while undoing or redoing away from a saved
state restores it; temporary pose-only history remains outside file dirty state.
Project FPS changes now participate in shared undo/redo and saved-state tracking.
Invalid startup file paths now report the load error and safely fall back to the
built-in example instead of leaving the editor with an empty document.
All destructive bone, slot, attachment, skin, and animation deletes now use a
consistent non-reversible warning dialog with Enter to confirm and Escape to cancel.
The bone context menu now prevents outside-click actions from passing through to
the editor, supports Up/Down/Enter/Escape navigation, and restores prior focus.
The bone hierarchy itself now supports Up/Down selection, Left/Right hierarchy
navigation, F2 rename, guarded Delete, Escape to clear search, and Down from search.
Bone and animation rename dialogs now select the current name, validate blank
input, and support Enter/Escape. Unchanged bone names no longer clear edit history.
Modal dialogs and focused context menus now suspend editor-wide shortcuts, so
playback, tools, file actions, and history cannot change behind an open prompt.
No-change feedback in the bone and attachment inspectors now uses normal status
styling, while genuine validation and operation failures remain highlighted.
Message and unsaved-change dialogs support Enter/Escape and reject duplicate decisions.
File and built-in loads now prepare skeleton and animation runtime state in an
isolated candidate document, then transfer ownership only after initialization succeeds.
Missing skeleton paths are rejected before atlas allocation with a precise error.
Structural edits now invalidate stale history and rebuild dependent panels through
a single coordinated refresh, avoiding duplicate timeline and inspector rebuilds.
The viewport toolbar now exposes Frame All directly in addition to selected-bone focus.
Animation create, duplicate, rename, and delete continue to invalidate stale
timeline history safely because they replace or restructure animation objects.
Deleting the active animation now selects its nearest list neighbor instead of
jumping to the first item; deleting the last animation stops and restores setup pose.
Bone key fields use semantic axis, angle, and scale-ratio labels, and single-axis
tracks show only the value that can actually be edited.
Slot RGBA color keys can be added, moved, edited as hexadecimal colors, copied,
pasted, and exported with linear or stepped interpolation.
Slot setup light color, optional two color dark tint, blend mode, and editor
visibility can be changed from the attachment inspector and preserved by JSON export.
Slots can be renamed without changing index-based animation or skin data.
Unreferenced tail slots can be deleted after guarded reference checks.
Skins can be created empty, duplicated, renamed, switched, and safely deleted.
The default skin remains protected to preserve Spine JSON semantics.

Supported input:
- Spine JSON skeleton (.json) with a same-name .atlas file
- Spine binary skeleton (.skel) with a same-name .atlas file
- Texture files referenced by the atlas must remain beside the atlas as exported

JSON export supports bones, slots, every standard attachment type, weighted and unweighted
meshes, linked meshes, sequences, Deform, IK, Transform, Path, and Physics setup/skin/timeline
data, including global Physics timelines. It also supports event definitions, draw order,
slot attachment and color timelines, discrete bone inheritance, and rotate/translate/scale/
shear timelines using linear, stepped, or Bezier curves. Remaining unsupported data is kept
intact in memory and export is blocked before writing to prevent silent data loss.

This package includes Spine Runtimes. Distribution and use are subject to the
Spine Runtimes License as well as the licenses of libGDX and LWJGL.
"@
$releaseNotes | Set-Content -LiteralPath (Join-Path $appImageDir "README.txt") -Encoding UTF8

$exePath = Join-Path $appImageDir "OpenSpine.exe"
if (-not (Test-Path -LiteralPath $exePath)) {
    throw "Release validation failed: $exePath was not created."
}
$mcpExePath = Join-Path $appImageDir "OpenSpineMCP.exe"
if (-not (Test-Path -LiteralPath $mcpExePath)) {
    throw "Release validation failed: $mcpExePath was not created."
}

$mcpSmokeProject = '{"skeleton":{"hash":"","spine":"4.3.23","x":0,"y":0,"width":0,"height":0},"bones":[{"name":"root"}]}'
$mcpModernMeta = @{
	"io.modelcontextprotocol/protocolVersion" = "2026-07-28"
	"io.modelcontextprotocol/clientCapabilities" = @{}
}
$mcpRequests = @(
	(@{
		jsonrpc = "2.0"
		id = 1
		method = "initialize"
		params = @{
			protocolVersion = "2025-11-25"
			capabilities = @{}
			clientInfo = @{ name = "openspine-release-smoke"; version = $Version }
		}
	} | ConvertTo-Json -Compress -Depth 10),
	(@{ jsonrpc = "2.0"; method = "notifications/initialized" } | ConvertTo-Json -Compress -Depth 10),
	(@{ jsonrpc = "2.0"; id = 2; method = "tools/list"; params = @{} } |
		ConvertTo-Json -Compress -Depth 10),
	(@{
		jsonrpc = "2.0"
		id = 3
		method = "tools/call"
		params = @{
			name = "openspine_validate_project"
			arguments = @{ json = $mcpSmokeProject }
		}
	} | ConvertTo-Json -Compress -Depth 10),
	(@{ jsonrpc = "2.0"; id = 4; method = "server/discover"; params = @{ _meta = $mcpModernMeta } } |
		ConvertTo-Json -Compress -Depth 10),
	(@{ jsonrpc = "2.0"; id = 5; method = "tools/list"; params = @{ _meta = $mcpModernMeta } } |
		ConvertTo-Json -Compress -Depth 10),
	(@{
		jsonrpc = '2.0'; id = 6; method = 'tools/call'
		params = @{
			name = 'openspine_validate_project'
			arguments = @{ json = '{"skeleton":{"spine":"4.3.0"},"bones":[{"name":"root"}],"animations":{"idle":{"events":[]}}}' }
		}
	} | ConvertTo-Json -Compress -Depth 10),
	(@{
		jsonrpc = '2.0'; id = 7; method = 'tools/call'
		params = @{
			name = 'openspine_validate_project'
			arguments = @{ json = '{"skeleton":{"spine":"4.3.0"},"bones":[{"name":"root"}],"animations":{"idle":{}}}' }
		}
	} | ConvertTo-Json -Compress -Depth 10)
)
$mcpOutput = @($mcpRequests | & $mcpExePath --root $projectRoot)
if ($LASTEXITCODE -ne 0) {
    throw "Release validation failed: OpenSpineMCP.exe exited with code $LASTEXITCODE."
}
if ($mcpOutput.Count -ne 7) {
	throw "Release validation failed: expected seven MCP responses, got $($mcpOutput.Count)."
}
$mcpResponses = @($mcpOutput | ForEach-Object { $_ | ConvertFrom-Json })
$mcpResponsesById = @{}
foreach ($response in $mcpResponses) {
	if ($null -eq $response.id) {
		throw "Release validation failed: packaged MCP returned a response without an id."
	}
	$mcpResponsesById[[string]$response.id] = $response
}
foreach ($requiredId in 1..7) {
	if (-not $mcpResponsesById.ContainsKey([string]$requiredId)) {
		throw "Release validation failed: packaged MCP response $requiredId is missing."
	}
}
$legacyTools = @($mcpResponsesById["2"].result.tools)
$modernTools = @($mcpResponsesById["5"].result.tools)
$mcpToolNames = @($modernTools | ForEach-Object { $_.name })
$missingOutputSchemas = @($modernTools | Where-Object { $null -eq $_.outputSchema })
$validationResult = $mcpResponsesById["3"].result
if ($mcpResponsesById["1"].result.protocolVersion -ne "2025-11-25" -or
	$mcpResponsesById["1"].result.serverInfo.build.source_sha256 -ne $buildInfo.source_sha256 -or
	([DateTimeOffset]$mcpResponsesById["1"].result.serverInfo.build.built_at) -ne ([DateTimeOffset]$buildInfo.built_at) -or
	-not $mcpResponsesById["1"].result.serverInfo.version.EndsWith('+' + $buildInfo.source_sha256.Substring(0, 12)) -or
	[string]::IsNullOrWhiteSpace($mcpResponsesById["1"].result.instructions) -or
	$legacyTools.Count -ne 8 -or
	$modernTools.Count -ne 8 -or
	$mcpResponsesById["4"].result.supportedVersions[0] -ne "2026-07-28" -or
	$mcpToolNames -notcontains "openspine_list_projects" -or
	$mcpToolNames -notcontains "openspine_read_project_chunk" -or
	$mcpToolNames -notcontains "openspine_render_preview" -or
	$mcpToolNames -notcontains "openspine_capture_screenshot" -or
	$missingOutputSchemas.Count -ne 0 -or
	$validationResult.isError -or
	-not $validationResult.structuredContent.valid -or
	-not $validationResult.structuredContent.structure_valid -or
	$validationResult.structuredContent.textures_checked -or
	$validationResult.structuredContent.render_checked -or
	$validationResult.structuredContent.summary.counts.bones -ne 1) {
	throw "Release validation failed: packaged MCP handshake, schemas, discovery, or tool execution is incomplete."
}
if (-not (Test-Path -LiteralPath (Join-Path $appImageDir 'OpenSpinePreview.exe') -PathType Leaf)) {
	throw 'Release validation failed: OpenSpinePreview.exe is missing.'
}
$emptyTimelineResult = $mcpResponsesById['6'].result
$omittedTimelineResult = $mcpResponsesById['7'].result
if (-not $emptyTimelineResult.isError -or
	$emptyTimelineResult.content[0].text -notmatch 'animations\.idle\.events' -or
	$emptyTimelineResult.content[0].text -notmatch 'at least one keyframe' -or
	$omittedTimelineResult.isError -or -not $omittedTimelineResult.structuredContent.valid) {
	throw 'Release validation failed: empty timeline diagnostics or omitted timeline compatibility regressed.'
}

Write-Host "[5/5] Creating archive and checksum..."
$archiveCreated = $false
for ($attempt = 1; $attempt -le 3; $attempt++) {
    try {
        Compress-Archive -LiteralPath $appImageDir -DestinationPath $archivePath -CompressionLevel Optimal
        $archiveCreated = $true
        break
    } catch {
        if ($attempt -eq 3) {
            throw
        }
        Write-Warning "Archive creation attempt $attempt failed; retrying after transient file locks clear."
        Remove-ManagedPath $archivePath
        Start-Sleep -Milliseconds (500 * $attempt)
    }
}
if (-not $archiveCreated) {
    throw "Unable to create release archive."
}
$hash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $(Split-Path -Leaf $archivePath)" | Set-Content -LiteralPath $checksumPath -Encoding ASCII

$archiveSize = [Math]::Round((Get-Item -LiteralPath $archivePath).Length / 1MB, 2)
Write-Host ""
Write-Host "Release created successfully."
Write-Host "Application: $exePath"
Write-Host "MCP server:  $mcpExePath"
Write-Host "Archive:     $archivePath ($archiveSize MB)"
Write-Host "SHA-256:     $hash"
