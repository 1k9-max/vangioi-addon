# Van Gioi Client fork

This project is a modified GPL-3.0 fork of Meteor Client, based on the supplied upstream source archive at commit `a96efdcdd60ed226650f6fc7f952ba65371bfc4d`.

The fork integrates the Van Gioi addon source from this workspace under `src/main/java/com/example/vangioi` and its resources under `src/main/resources`. The module implementation is authored for Van Gioi; the surrounding Meteor framework remains credited to Meteor Development and upstream contributors. The project remains GPL-3.0. See `LICENSE` and retain upstream copyright notices when redistributing.

The fork keeps the internal Fabric mod id `meteor-client` because Meteor source and its saved configuration use that id. Its displayed name and jar name identify it as Van Gioi Client, a Meteor-based fork. Do not install this fork alongside the official Meteor Client jar; it replaces that client runtime.

The `moon` chat word opens the module GUI. Meteor's default Right Shift and period shortcuts are unbound, and its Accounts/Proxies multiplayer overlay is removed. The separate project at the workspace root remains a conventional Meteor addon build; use this fork's jar when you want the modules integrated into the Meteor source tree.

## Build with GitHub Actions

Put the contents of `van-gioi-client-*-sources.zip` at the root of your GitHub repository. The included `.github/workflows/build.yml` builds on push, pull request, or manual dispatch and uploads the client jar and source ZIP as workflow artifacts. If building locally after extracting the ZIP, run `chmod +x gradlew` once, then `./gradlew build` with Java 21.
