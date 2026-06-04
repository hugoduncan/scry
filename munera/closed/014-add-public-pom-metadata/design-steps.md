# Design follow-up steps

- [x] Pin the exact public metadata values for both core and adapter POMs: artifact names, descriptions, project URL, SCM URL/connection/developerConnection/tag policy, and maintainer/developer fields or an explicit decision to omit any of them.
- [x] Specify the exact Maven license metadata to emit from the EPL-2.0 handoff, including license name, URL, distribution/comments if any, and whether the SPDX id appears in the name or elsewhere.
- [x] Clarify whether metadata verification must inspect the filesystem POMs used for deploy, the POMs embedded inside the jars, or both for each artifact.
