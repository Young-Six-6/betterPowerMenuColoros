# BetterPowerMenu animation pack

`pack.json` maps the original ColorOS power-menu Lottie views to editable JSON files.

- `reboot_icon.json`: original reboot icon; progress follows upward dragging.
- `shutdown_icon.json`: original shutdown icon; progress follows downward dragging.
- `global_actions_arrow.json`: looping road-arrow animation, reused for both directions.

Keep the `format` value at `1`. File names may contain letters, digits, `_`, `-`, and `.` only.
Each animation must be valid Lottie JSON containing `v`, `w`, `h`, and `layers`.

If the pack is missing, malformed, or incompatible with a newer SystemUI, the module leaves
the system animation untouched. Rebuild the APK and restart SystemUI after editing the pack.

The bar/handler spring, alpha, text, and gesture transitions are code-driven animations rather
than Lottie assets. The enhanced bar equivalents remain editable in `SideSwipeBarView.java`.
