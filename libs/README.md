# Vendor artifact provenance

`regulus-bo18-0.0.0-dev.jar` was extracted without modification from
`current_deployment/app.jar!/BOOT-INF/lib/regulus-bo18-0.0.0-dev.jar` supplied by the user.
It contains Java 8 bytecode (major 52). No vendor source, POM, license or published coordinates were supplied.
Use within the team's existing license; do not publish the artifact to a public repository.

Embedded/selected SHA-256:
`3941326c55c4415341fd929083a35bdb00ec4aefc14e313906b027b62ef5dd0e`

The separate deployment-folder JAR with the same filename is **different**:
`deead1987c397d66dbd25c8b51e37d0522b89f719961381ac89d0ca6f7080f2a`.
The embedded copy was selected because `app.jar`'s JarLauncher loads its nested dependencies.
The Gradle build checks the selected checksum. Review any vendor update explicitly.

`jdeps` identified Apache Commons IO references, declared explicitly in Gradle. Vendor
JSON handling is proprietary (`jsonUtil`), not Jackson. The application itself uses Jackson
through Spring MVC. The old executable bundled Guava, but neither the supplied application
sources nor the selected vendor binary references it; it is not added as an unused dependency.

For a future private Maven repository, obtain authoritative coordinates and a dependency POM
from the owning team, publish there, and replace the local file dependency. Do not invent
public Maven coordinates for this artifact.
