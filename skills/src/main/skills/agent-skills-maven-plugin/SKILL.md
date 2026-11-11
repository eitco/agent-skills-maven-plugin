---
name: agent-skills-maven-plugin
description: >-
  Erklärt die Nutzung des agent-skills-maven-plugin (de.eitco.cicd:agent-skills-maven-plugin), das
  Agent-Skills (Verzeichnis mit SKILL.md) als skillctl-kompatible OCI-Images baut, testet und in
  OCI-Registries wie Nexus pusht – ohne docker, podman oder skillctl. Heranziehen, sobald ein
  Projekt mit `<packaging>skill</packaging>` angelegt oder geändert wird, Skills unter
  src/main/skills abgelegt, das SKILL.md-Frontmatter (name, description, status) geschrieben,
  eine tests.yaml für Skript-Tests erstellt, Registry/Repository/Credentials für den Push
  konfiguriert, ein Goal (build, test, archive, push) gebunden oder ein Fehler des Plugins
  untersucht wird (Validierung, statische Checks, Push, TLS). Deckt Aktivierung und Lifecycle,
  Projektlayout, Frontmatter-Regeln, generierte skill.yaml, Status und Tags, statische Checks,
  Skript-Tests, Credentials, alle Parameter sowie die Stolpersteine ab.
---

# Nutzung des agent-skills-maven-plugin

Das `agent-skills-maven-plugin` (`de.eitco.cicd:agent-skills-maven-plugin`, Prefix `agent-skills`) baut
**Agent-Skills** im [agent skills](https://agentskills.io)-Format zu **OCI-Images** und pusht sie in
OCI-Registries (z. B. Sonatype Nexus). Das Image-Format ist kompatibel zu
[skillctl](https://github.com/redhat-et/skillimage); `skillctl`, docker oder podman werden nicht benötigt.

```text
src/main/skills/<skill>/  --agent-skills:build-->  target/skill-oci/<skill>/ (OCI image layout)  --agent-skills:push-->  registry
```

Dieser Skill beschreibt das **Verwenden/Konfigurieren** des Plugins. Wer das Plugin selbst
erweitert, siehe [[maven-plugin-authoring]]. Für das Anlegen des Docker-Repositorys in Nexus siehe
[[nexus-repository-management]].

> Alle Angaben sind am Quellcode verifiziert (Mojos in `agent-skills-maven-plugin`, Regeln in
> `agent-skills-core`).

---

## 1. Aktivierung mit Packaging `skill` (empfohlen)

```xml
<project>
    <groupId>my.group</groupId>
    <artifactId>my-skills</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>skill</packaging>

    <build>
        <plugins>
            <plugin>
                <groupId>de.eitco.cicd.agentskills</groupId>
                <artifactId>agent-skills-maven-plugin</artifactId>
                <version>${agent-skills-maven-plugin.version}</version>
                <extensions>true</extensions>   <!-- Pflicht: macht das Packaging bekannt -->
                <configuration>
                    <namespace>cicd</namespace>  <!-- Pflicht, DNS-Label -->
                    <registry>nexus.eitco.de</registry>
                    <repository>docker-cicd-prerelease/eitco/cicd</repository>
                    <registryServerId>eitco-oci</registryServerId>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Lifecycle des Packagings `skill`:

| Phase     | Goals                                                   |
|-----------|---------------------------------------------------------|
| `test`    | `agent-skills:test` – statische Checks und Skript-Tests   |
| `package` | `agent-skills:build` – ein OCI-Image-Layout pro Skill     |
| `verify`  | `agent-skills:archive` – alle Layouts als `.tar`          |
| `install` | `maven-install-plugin:install`                          |
| `deploy`  | `agent-skills:push`, danach `maven-deploy-plugin:deploy`  |

* Das Archiv `target/<finalName>.tar` ist das **Hauptartefakt** (Typ `skill`, Extension `tar`) –
  `install`/`deploy` legen die Skills also zusätzlich in Maven-Repositories ab.
* `mvn install` pusht **nie**, der Push hängt an `deploy`. `-Dskill.push.skip=true` unterdrückt ihn immer.

## 2. Nutzung ohne Packaging `skill`

Die Goals lassen sich in jedem Projekt binden. Default-Phasen: `test`→test, `build`→package,
`archive`→verify, `push`→deploy.

```xml
<plugin>
    <groupId>de.eitco.cicd.agentskills</groupId>
    <artifactId>agent-skills-maven-plugin</artifactId>
    <version>${agent-skills-maven-plugin.version}</version>
    <configuration>
        <namespace>cicd</namespace>
        <registry>nexus.eitco.de</registry>
        <repository>docker-cicd-prerelease/eitco/cicd</repository>
        <registryServerId>eitco-oci</registryServerId>
    </configuration>
    <executions>
        <execution>
            <id>build-skill-image</id>
            <goals>
                <goal>test</goal>
                <goal>build</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

* `archive` hängt das Tar hier mit Classifier **`skills`** an (Typ `tar`).
* `push` **nur in einem CI-Profil** binden, damit lokale Builds nie veröffentlichen:

```xml
<profile>
    <id>publish-skill</id>
    <build>
        <plugins>
            <plugin>
                <groupId>de.eitco.cicd.agentskills</groupId>
                <artifactId>agent-skills-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>push-skill-image</id>
                        <phase>deploy</phase>
                        <goals><goal>push</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

`mvn deploy -Ppublish-skill` baut und pusht.

## 3. Projektlayout

```text
pom.xml
src/main/skills/
  nexus-test/              # ein Skill = ein direktes Unterverzeichnis
    SKILL.md               # Pflicht
    references/guide.md    # beliebige weitere Dateien
    scripts/hello.sh
src/test/skills/
  nexus-test/
    tests.yaml             # optional: Skript-Tests, NICHT Teil des Images
```

* Jedes **direkte, nicht versteckte** Unterverzeichnis von `src/main/skills` ist ein Skill
  (Verzeichnisse mit `.`-Prefix und Dateien werden ignoriert). Eine Ausführung baut/pusht alle.
* Ohne einen einzigen Skill schlägt jedes Goal fehl.
* Standardmäßig gehen alle Dateien ins Image außer `.git/**` und `target/**` (siehe `includes`/`excludes`).

## 4. Das Frontmatter von `SKILL.md`

```markdown
---
name: nexus-test
description: Test skill for OCI registry
status: draft          # optional
license: Apache-2.0    # weitere Felder werden unverändert übernommen
metadata:
  author: eitco
---

# Nexus Test
...
```

Regeln (alle Verstöße werden gesammelt gemeldet):

* `name` – DNS-Label (Kleinbuchstaben, Ziffern, `-`, alphanumerisch am Anfang/Ende, max. 63 Zeichen)
  und **identisch mit dem Verzeichnisnamen**.
* `description` – nicht leer, max. **1024** Zeichen.
* `status` – optional, `draft`, `testing` oder `published` (Groß/Klein egal); alles andere ist ein Fehler.
* `namespace` und `version` sind **verboten** – sie werden vom Plugin verwaltet (Plugin-Konfiguration).
  Einträge gleichen Namens unter `metadata:` sind erlaubt und bleiben unverändert.
* Eine `skill.yaml` im Skill-Verzeichnis ist ein Fehler – sie wird generiert.
* `SKILL.md` muss eine reguläre Datei sein; ein Symlink darf nicht aus dem Skill herauszeigen.

## 5. Generierte Dateien

`agent-skills:build` erzeugt pro Skill in `target/generated-skills/<skill>/` zwei Dateien, die **statt**
der Quellen ins Image gehen (die Quellen werden nie verändert):

`skill.yaml` (SkillCard):

```yaml
apiVersion: skillimage.io/v1alpha1
kind: SkillCard
metadata:
  name: nexus-test                          # aus dem Frontmatter
  namespace: cicd                           # <namespace>
  version: 0.0.1                            # <version>, default ${project.version}
  description: Test skill for OCI registry  # aus dem Frontmatter
spec:
  prompt: SKILL.md
```

`SKILL.md` – das Frontmatter wird neu geschrieben, `namespace` und `version` werden als
Top-Level-Felder **angehängt**; übrige Felder behalten ihre Reihenfolge, alles nach dem schließenden
`---` bleibt byte-genau erhalten. **Kommentare im Frontmatter gehen verloren.**

`namespace` und `version` gelten für alle Skills eines Builds und können aus einer Parent-POM geerbt werden.

## 6. Status und Tags

| `status` im Frontmatter      | Tag                 | Annotation `io.skillimage.status` |
|------------------------------|---------------------|-----------------------------------|
| – (default) oder `published` | `<version>`         | `published`                       |
| `testing`                    | `<version>-testing` | `testing`                         |
| `draft`                      | `<version>-draft`   | `draft`                           |

* SNAPSHOT-Versionen werden unverändert verwendet: `1.0.0-SNAPSHOT`, mit `draft` → `1.0.0-SNAPSHOT-draft`.
* Es wird **kein `latest`** erzeugt.
* Push-Ziel: `<registry>/<repository>/<name>:<tag>`, z. B.
  `nexus.eitco.de/docker-cicd-prerelease/eitco/cicd/nexus-test:0.0.1`.

## 7. Tests (`agent-skills:test`)

Schlagende Tests lassen den Build fehlschlagen. Ergebnisse landen als Surefire-Reports in
`target/surefire-reports`, CI-Server zeigen sie wie normale Tests.

### 7.1 Statische Checks – immer aktiv, Suite `skill.<skill>.static`

| Check         | schlägt fehl bei                                                                                     |
|---------------|------------------------------------------------------------------------------------------------------|
| `frontmatter` | Verstößen gegen die Regeln aus Abschnitt 4                                                          |
| `links`       | relativen Links/Bildern in `*.md`, die auf fehlende, ausgeschlossene oder skill-externe Dateien zeigen |
| `scripts`     | ausführbaren Textdateien in `scripts/` ohne Shebang (`#!`); nicht ausführbare = nur Warnung         |
| `orphans`     | nur Warnung: Dateien, die von keiner Markdown-Datei verlinkt oder erwähnt werden                    |
| `size`        | nur Warnung: `SKILL.md` mit mehr als 500 Zeilen                                                     |

* `-Dskill.test.failOnWarning=true` lässt auch Warnungen fehlschlagen.
* Links in Code-Blöcken und Inline-Code werden ignoriert; URLs und reine Anker (`#…`) ebenfalls.
* Als „erwähnt“ (`orphans`) gilt auch, wenn der relative Pfad irgendwo im Markdown-Text steht.

### 7.2 Skript-Tests – `src/test/skills/<skill>/tests.yaml`, Suite `skill.<skill>.scripts`

Die Tests laufen gegen eine **Kopie der Dateien, die ins Image gehen** (inkl. Executable-Bits) in
`target/skill-tests/<skill>`. Arbeitsverzeichnis ist diese Kopie, zusätzlich als `$SKILL_DIR` verfügbar.

```yaml
tests:
  - name: hello greets
    command: [scripts/hello.sh, world]   # erstes Element mit '/' wird relativ zum Skill aufgelöst
    stdin: ""                            # optional
    env: { GREETING: Hello }             # optional, ergänzt die Umgebung
    timeout: 30s                         # optional, ms|s|m|h, default 60s
    exitCode: 0                          # default 0
    stdout: { contains: [Hello], matches: ["^Hello, world$"], notMatches: [error] }
    stderr: { empty: true }              # empty: false = darf nicht leer sein
```

* `matches`/`notMatches` sind Java-Regexe, `^`/`$` matchen an Zeilengrenzen; gesucht wird (find), nicht ganz gematcht.
* Kommandos ohne `/` im ersten Element werden über den `PATH` gesucht (z. B. `[bash, -c, "…"]`).
* Fehlt `name`, wird das Kommando als Testname verwendet.

Überspringen: `-Dskill.test.skip=true`, `-DskipTests` oder `-Dmaven.test.skip=true`.

## 8. Push, Credentials und TLS (`agent-skills:push`)

Credentials stehen **nie in der POM**, sondern im `<server>` mit der ID `registryServerId` der
`settings.xml` (verschlüsselte Passwörter werden unterstützt):

```xml
<servers>
    <server>
        <id>eitco-oci</id>
        <username>${env.NEXUS_USERNAME}</username>
        <password>${env.NEXUS_PASSWORD}</password>
    </server>
</servers>
```

* Ohne `registryServerId` wird anonym gepusht; eine angegebene, aber fehlende Server-ID ist ein Fehler.
* `push` liest die Layouts aus `target/skill-oci` – vorher muss `build` gelaufen sein. Alle Layouts
  werden geprüft, **bevor** das erste Image gepusht wird.
* Nach dem Push wird der von der Registry bestätigte Digest mit dem lokalen verglichen (`verifyDigest`).
* Zusätzliche CAs: `caCertificates` (PEM-Dateien) oder `trustStore`/`trustStorePassword`/`trustStoreType`
  – jeweils **zusätzlich** zu den JVM-Defaults. TLS-Prüfung lässt sich nicht abschalten.
* `allowPlainHttp` nur für lokale Test-Registries (loggt eine Warnung).

## 9. Parameter-Referenz

Gemeinsam für alle Goals:

| Parameter             | Property                | Default                                 |
|-----------------------|-------------------------|-----------------------------------------|
| `skillsDirectory`     | `skill.skillsDirectory` | `${project.basedir}/src/main/skills`    |
| `outputDirectory`     | `skill.outputDirectory` | `${project.build.directory}/skill-oci`  |
| `skillTestsDirectory` | `skill.testsDirectory`  | `${project.basedir}/src/test/skills`    |

`build` und `test` (Inhalt der Images):

| Parameter     | Property          | Default                | Beschreibung                                         |
|---------------|-------------------|------------------------|------------------------------------------------------|
| `includes`    |                   | `**/*`                 | Ant-Patterns relativ zum Skill                       |
| `excludes`    |                   | `.git/**`, `target/**` | gewinnt gegen `includes`; **ersetzt** die Defaults   |
| `executables` |                   |                        | Ant-Patterns für Mode `0755` (zusätzlich zum FS-Bit) |
| `namespace`   | `skill.namespace` | – (**Pflicht**)        | DNS-Label für alle Skills                            |
| `version`     | `skill.version`   | `${project.version}`   | bestimmt den Tag                                     |

Nur `build`: `generatedSkillsDirectory` (`skill.generatedSkillsDirectory`,
default `${project.build.directory}/generated-skills`).

Nur `test`:

| Parameter          | Property                      | Default                                       |
|--------------------|-------------------------------|-----------------------------------------------|
| `failOnWarning`    | `skill.test.failOnWarning`    | `false`                                       |
| `reportsDirectory` | `skill.test.reportsDirectory` | `${project.build.directory}/surefire-reports` |
| `skip`             | `skill.test.skip`             | `false`                                       |

Nur `archive`: `archiveFile` (`skill.archive.file`,
default `${project.build.directory}/${project.build.finalName}.tar`).

Nur `push`:

| Parameter            | Property                        | Default |
|----------------------|---------------------------------|---------|
| `registry`           | `skill.registry`                |         |
| `repository`         | `skill.repository`              |         |
| `registryServerId`   | `skill.registryServerId`        |         |
| `verifyDigest`       | `skill.push.verifyDigest`       | `true`  |
| `caCertificates`     |                                 |         |
| `trustStore`         | `skill.push.trustStore`         |         |
| `trustStorePassword` | `skill.push.trustStorePassword` |         |
| `trustStoreType`     | `skill.push.trustStoreType`     | JVM-Default |
| `allowPlainHttp`     | `skill.push.allowPlainHttp`     | `false` |
| `skip`               | `skill.push.skip`               | `false` |

## 10. Typische Aufrufe

| Ziel                                     | Aufruf                                          |
|------------------------------------------|-------------------------------------------------|
| Skills prüfen und bauen, nichts pushen   | `mvn verify`                                    |
| lokal ins Maven-Repo installieren        | `mvn install`                                   |
| in CI bauen und pushen (Packaging skill) | `mvn deploy`                                    |
| deploy ohne OCI-Push                     | `mvn deploy -Dskill.push.skip=true`             |
| gezielt pushen                           | `mvn package agent-skills:push`                   |
| schnell bauen ohne Tests                 | `mvn package -DskipTests`                       |

## 11. Image-Format (Kurzfassung)

* Ein `tar+gzip`-Layer mit allen ausgewählten Dateien (generierte `SKILL.md` und `skill.yaml` statt der Quellen).
* Normalisiert: Einträge nach UTF-8-Pfad sortiert, uid/gid `0`, Mode `0644` (`0755` für Verzeichnisse
  und Executables), alle Zeitstempel = **Startzeit des Maven-Builds**. Jeder Build erzeugt daher neue
  Digests – die Images sind nicht reproduzierbar.
* Config `linux/amd64`; Manifest-Annotationen u. a. `io.skillimage.status`, `io.skillimage.wordcount`,
  `org.opencontainers.image.{created,description,title,vendor,version}`.

## 12. Stolpersteine

* **`<extensions>true</extensions>` vergessen** → Maven kennt das Packaging `skill` nicht.
* **`namespace` fehlt** → `build`/`test` schlagen fehl (Pflichtparameter).
* **`namespace`/`version` im Frontmatter** → Validierungsfehler; im Plugin konfigurieren.
* **`name` ≠ Verzeichnisname** → Validierungsfehler.
* **`skill.yaml` von Hand angelegt** → Validierungsfehler; die Datei löschen.
* Die Meldung „no skills found in … – every subdirectory is expected to contain a skill with skill.yaml“
  ist veraltet: gemeint ist ein Unterverzeichnis (mit `SKILL.md`), `skill.yaml` wird nicht erwartet.
* **`excludes` gesetzt** → `.git/**` und `target/**` sind nicht mehr ausgeschlossen, ggf. wieder aufnehmen.
* **Skript ausführbar, aber ohne Shebang** → Fehler im Check `scripts`; nicht ausführbar → nur Warnung
  (Bit setzen oder in `<executables>` aufnehmen).
* **Link auf ausgeschlossene Datei** → Fehler im Check `links`, auch wenn die Datei auf der Platte existiert.
* **`push` ohne vorheriges `build`** → kein Layout in `target/skill-oci`; erst `package` laufen lassen.
* **Repository-Pfad mit Großbuchstaben** → ungültiges Push-Ziel; nur Kleinbuchstaben, Ziffern und `.`, `_`, `-`.
* **Registry mit Schema** (`https://…`) wird toleriert und entfernt, ein Pfad gehört aber in `<repository>`.
