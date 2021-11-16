**DependencyATLoader**:

This is a 1.12.2 FG 3+ hack copied from FG 2.3's `GradleForgeHacks`, made to allow 1.12 dependencies to have their Access Transformers loaded through the classpath.

Can be used by having `setMain("net.msrandom.atload.ClientGameStarter")` on the client run and `setMain("net.msrandom.atload.ClientGameStarter")` on the server run in `build.gradle`.
