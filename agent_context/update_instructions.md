# Update Instructions

## When to Update Context

Update context files when:
- Architecture changes significantly
- New APIs are added
- Business rules change
- New constraints are discovered

## How to Update

1. Edit the relevant `.md` file
2. Run `context-builder build` to regenerate summaries
3. Commit changes to version control

## Format

Use diff patches when proposing changes:

```diff
--- a/agent_context/decisions_log.md
+++ b/agent_context/decisions_log.md
@@ -1,3 +1,7 @@
+ ## 2026-01-15: New Decision
+ Description of the change
```

## Safety

- Human approval required for all changes
- Never modify generated/ files directly
- Keep context files concise but complete
