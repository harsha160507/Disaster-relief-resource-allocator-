# Supplied component integration

The user supplied components.zip as the Phase 5 design foundation. The application
uses a selected, consistent subset rather than mounting every alternative.

| Supplied material | Integration |
| --- | --- |
| kit.css | Shared token/button foundation; coordinated light/dark blue theme |
| Data table | React adaptation with server paging, literal search, sortable headers and stable record actions |
| Sidebar | Role-aware desktop navigation, organization context and user controls |
| Dialog | React/native-dialog adaptation with safe text, focus containment and restoration |
| Forms / selects | Labelled React controls, searchable server-backed selectors and field errors |
| States | Empty/error/retry and skeleton foundations |
| Count up | Supplied React implementation for dashboard summaries |
| Squish switch | Supplied React implementation for the saved theme switch |
| Spring check | Supplied React implementation for perishable-resource selection |
| Stepper | Supplied React implementation for request progress |
| Status mark | Retained supplied implementation for compatible future state displays |

Imported React component files use .tsx; CSS filenames match their imports.
Unmodified supplied React implementations retain their own formatting in
src/components/supplied. Adapted shared CSS retains supplied attribution comments.
The application's adapters, workflow code and theme overrides are formatted with
the project's Prettier configuration.

The gold SUBSCRIBE example, global destructive-button rules, hover-blurred cards,
icon-only dock navigation and automatic swipe-to-delete defaults are not used.
Operational cancellation remains an explicit, confirmed backend action.
Desktop layouts are the approved scope. No mobile workflow is claimed.

Important repairs include stable table record selection after sorting/filtering,
safe text rendering rather than interpolated innerHTML, keyboard-aware selectors,
cleaned-up event listeners, native modal focus handling and honest distinction
between allocation and delivery progress.

New frontend dependencies are pinned in package-lock.json. Review upstream
component origins/attribution when redistributing separately; this document records
integration provenance rather than asserting a new third-party license.
