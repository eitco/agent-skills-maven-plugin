---
name: agent-skills-maven-plugin
description: >-
  Erklärt, wie man mit dem agent-skills-maven-plugin (de.eitco.cicd.agentskills:agent-skills-maven-plugin,
  Prefix agent-skills) Agent-Skills (Verzeichnis mit SKILL.md) baut, testet und veröffentlicht: als
  skillctl-kompatible OCI-Images in Registries wie Nexus, als Skill-Jars in Maven-Repositories und in
  Git-Repositories für npx skills – ohne docker, podman oder skillctl. Heranziehen, sobald ein Projekt
  mit `<packaging>skill</packaging>` angelegt oder geändert wird, Skills unter src/main/skills abgelegt,
  das SKILL.md-Frontmatter (name, description, status) geschrieben, eine tests.yaml erstellt,
  Registry/Repository/Credentials für den Push oder gitUrl für git-deploy konfiguriert, ein Goal
  (test, build, archive, push, git-deploy) gebunden oder ein Fehler dabei untersucht wird (Validierung,
  statische Checks, Push, TLS, Git). Das Installieren von Skills in ein Projekt (sync, update, pull,
  skills.lock.yaml, .claude/skills) beschreibt agent-skills-maven-plugin-install.
---

# Skills bauen und veröffentlichen mit dem agent-skills-maven-plugin

Das `agent-skills-maven-plugin` (`de.eitco.cicd.agentskills:agent-skills-maven-plugin`, Prefix `agent-skills`)
baut **Agent-Skills** im [agent skills](https://agentskills.io)-Format und veröffentlicht sie auf drei Wegen:

* als **OCI-Images** in OCI-Registries (z. B. Sonatype Nexus), kompatibel zu
  [skillctl](https://github.com/redhat-et/skillimage) – `skillctl`, docker oder podman werden nicht benötigt,
* als **Skill-Jars** in Maven-Repositories (`install`/`deploy`),
* in **Git-Repositories**, aus denen [`npx skills`](https://github.com/vercel-labs/skills) installiert.

```text
src/main/skills/<skill>/  --build-->  target/skill-oci/<skill>/ (OCI image layout)  --push-->        registry
                                                                                     --git-deploy-->  git
                                      --archive-->  target/<finalName>.jar  --deploy-->  maven repository
```

Dieser Skill behandelt die **Produzenten-Seite**. Wie ein Projekt veröffentlichte Skills nach `.claude/skills`
installiert (`sync`, `update`, `pull`), steht in [[agent-skills-maven-plugin-install]]. Wer das Plugin selbst
erweitert: [[maven-plugin-authoring]]. Docker-Repository in Nexus anlegen: [[nexus-repository-management]].

> **Umbenannt in 0.0.5:** bis 0.0.4 hieß das Plugin `oci-skills-maven-plugin` (groupId `de.eitco.cicd.ociskills`,
> Prefix `oci-skills`). Jars von 0.0.3/0.0.4 lassen sich weiter installieren.

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

| Phase     | Goals                                                                               |
|-----------|-------------------------------------------------------------------------------------|
| `test`    | `agent-skills:test` – statische Checks und Skript-Tests                              |
| `package` | `agent-skills:build` – ein OCI-Image-Layout pro Skill                                |
| `verify`  | `agent-skills:archive` – Skill-Jar aller Skills plus ein Jar pro Skill              |
| `install` | `maven-install-plugin:install`                                                      |
| `deploy`  | `agent-skills:push`, `agent-skills:git-deploy`, danach `maven-deploy-plugin:deploy` |

* Hauptartefakt ist `target/<finalName>.jar` (Typ `skill`, Extension `jar`) mit allen Skills.
* Zusätzlich wird **jeder Skill einzeln** als Jar angehängt (Typ `jar`), Classifier = Skill-Name, bei Status
  ≠ `published` mit Suffix: `nexus-test`, `nexus-test-draft`. Diese Jars installieren andere Projekte.
* `mvn install` veröffentlicht **nie** in Registry oder Git – Push und git-deploy hängen an `deploy`.
* `-Dskill.push.skip=true` unterdrückt den OCI-Push, `-Dskill.git.skip=true` das git-deploy – unabhängig voneinander.
* `git-deploy` tut ohne `gitUrl` nichts, kann im Lifecycle also unkonfiguriert bleiben.

## 2. Nutzung ohne Packaging `skill`

Die Goals lassen sich in jedem Projekt binden. Default-Phasen: `test`→test, `build`→package,
`archive`→verify, `push`/`git-deploy`→deploy.

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
                <goal>archive</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

* `archive` hängt das Jar aller Skills hier mit Classifier **`skills`** an, jeden Skill wie oben mit eigenem Classifier.
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
der Quellen ins Image (und ins Jar) gehen – die Quellen werden nie verändert:

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
Namespace und Name bilden die Skill-ID `<namespace>/<name>`, über die Konsumenten den Skill anfordern.

## 6. Status und Tags

| `status` im Frontmatter      | Tag / Classifier-Suffix | Annotation `io.skillimage.status` |
|------------------------------|-------------------------|-----------------------------------|
| – (default) oder `published` | `<version>`             | `published`                       |
| `testing`                    | `<version>-testing`     | `testing`                         |
| `draft`                      | `<version>-draft`       | `draft`                           |

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

## 9. Git-Deploy für `npx skills` (`agent-skills:git-deploy`)

```xml
<configuration>
    <gitUrl>https://git.example.com/ai/skills.git</gitUrl>
    <gitServerId>my-git</gitServerId>
</configuration>
```

Beim `deploy` klont das Goal den Branch `gitBranch` (default `main`, wird bei Bedarf angelegt), ersetzt die
Verzeichnisse `<gitDirectory>/<skill>/` (default `skills/`) durch die Dateien der Images – inkl. generierter
`skill.yaml` und Executable-Bits – und committet, falls sich etwas geändert hat. Alle übrigen Dateien bleiben,
mehrere Projekte können also in dasselbe Repository veröffentlichen. Der Commit wird mit
`<artifactId>-<version>` getaggt:

```shell
npx skills add https://git.example.com/ai/skills.git#my-skills-1.2.0 --skill nexus-test   # gepinnt
npx skills add https://git.example.com/ai/skills.git --skill nexus-test                   # folgt main
npx skills update                                                                         # ungepinnte
```

* Nur Skills mit Status `published` werden deployt – `<gitStatuses>` wählt andere.
* SNAPSHOTs werden nur deployt, wenn `gitSnapshotBranch` gesetzt ist – dorthin und **ohne Tag**.
* Existiert der Tag schon (z. B. wiederholter Release-Build), bleibt er mit Warnung unverändert; der Branch
  wird trotzdem aktualisiert.
* Wurde der Push abgelehnt, weil ein anderer Build dazwischen gepusht hat, wird auf einem frischen Klon bis zu
  `gitAttempts`-mal (default 3) wiederholt.
* Credentials aus dem `<server>` `gitServerId` der `settings.xml` (Benutzer/Passwort oder Access-Token).
  Nur `https`- und `file`-URLs, **kein ssh**.
* Nur Git, keine Registry: `mvn deploy -Dskill.push.skip=true`.

## 10. Skill-Jar (`agent-skills:archive`)

Das Jar eines einzelnen Skills und das Jar aller Skills haben dasselbe Layout:

```text
nexus-test/                                     # Skill-Dateien, exakt wie im Layer
nexus-test/SKILL.md                             #   Modes 0644, 0755 für Verzeichnisse und Executables
nexus-test/skill.yaml
nexus-test/scripts/run.sh
META-INF/agent-skills/nexus-test/manifest.json  # Manifest des Images, byte-identisch
META-INF/agent-skills/nexus-test/config.json    # Config des Images, byte-identisch
```

* Verzeichnis = `name` des Skills: Entpacken nach `.claude/skills` (z. B. `dependency:unpack`) ergibt
  `.claude/skills/nexus-test/SKILL.md`.
* Reproduzierbar: Einträge nach Pfad sortiert, Zeitstempel = `created`-Annotation des Images, kein `MANIFEST.MF`.
* Der Digest des Images bleibt erhalten (SHA-256 von `manifest.json`) – ein aus der Registry gelockter Skill
  kann aus dem Maven-Repository installiert werden und umgekehrt.

## 11. Parameter-Referenz

Gemeinsam für alle Produzenten-Goals:

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
default `${project.build.directory}/${project.build.finalName}.jar`); Einzel-Jars liegen daneben
(`target/<finalName>-nexus-test.jar`).

Nur `push`:

| Parameter            | Property                        | Default     |
|----------------------|---------------------------------|-------------|
| `registry`           | `skill.registry`                |             |
| `repository`         | `skill.repository`              |             |
| `registryServerId`   | `skill.registryServerId`        |             |
| `verifyDigest`       | `skill.push.verifyDigest`       | `true`      |
| `caCertificates`     |                                 |             |
| `trustStore`         | `skill.push.trustStore`         |             |
| `trustStorePassword` | `skill.push.trustStorePassword` |             |
| `trustStoreType`     | `skill.push.trustStoreType`     | JVM-Default |
| `allowPlainHttp`     | `skill.push.allowPlainHttp`     | `false`     |
| `skip`               | `skill.push.skip`               | `false`     |

Nur `git-deploy`:

| Parameter           | Property                   | Default                                            |
|---------------------|----------------------------|----------------------------------------------------|
| `gitUrl`            | `skill.git.url`            | – (ohne: Goal tut nichts)                          |
| `gitServerId`       | `skill.git.serverId`       |                                                    |
| `gitBranch`         | `skill.git.branch`         | `main`                                             |
| `gitSnapshotBranch` | `skill.git.snapshotBranch` | – (ohne: SNAPSHOTs übersprungen)                   |
| `gitDirectory`      | `skill.git.directory`      | `skills`                                           |
| `gitTag`            | `skill.git.tag`            | `${project.artifactId}-${project.version}`, leer = kein Tag |
| `gitCommitMessage`  | `skill.git.commitMessage`  | `release ${project.artifactId} ${project.version}` |
| `gitStatuses`       |                            | `published`                                        |
| `gitAuthorName`     | `skill.git.authorName`     | `agent-skills-maven-plugin`                        |
| `gitAuthorEmail`    | `skill.git.authorEmail`    | `agent-skills-maven-plugin@noreply.invalid`        |
| `gitAttempts`       | `skill.git.attempts`       | `3`                                                |
| `gitWorkDirectory`  | `skill.git.workDirectory`  | `${project.build.directory}/skill-git` (wird vorher gelöscht) |
| `skip`              | `skill.git.skip`           | `false`                                            |

## 12. Typische Aufrufe

| Ziel                                     | Aufruf                                          |
|------------------------------------------|-------------------------------------------------|
| Skills prüfen und bauen, nichts pushen   | `mvn verify`                                    |
| lokal ins Maven-Repo installieren        | `mvn install`                                   |
| in CI bauen und veröffentlichen          | `mvn deploy`                                    |
| deploy ohne OCI-Push (z. B. nur Git)     | `mvn deploy -Dskill.push.skip=true`             |
| deploy ohne git-deploy                   | `mvn deploy -Dskill.git.skip=true`              |
| gezielt pushen                           | `mvn package agent-skills:push`                 |
| schnell bauen ohne Tests                 | `mvn package -DskipTests`                       |

## 13. Konsum ohne Maven (skillctl, npx skills)

Wie Konsumenten ohne Maven an die veröffentlichten Skills kommen – nur das, was die Kompatibilitätstests des
Plugins abdecken (gepinnt: skillctl 0.7.2, `npx skills`). Alles Weitere steht in der Doku der jeweiligen Tools.
Mit Maven installiert man über [[agent-skills-maven-plugin-install]].

```shell
skillctl pull nexus.eitco.de/docker-cicd-prerelease/eitco/cicd/nexus-test:0.0.1         # published
skillctl pull nexus.eitco.de/docker-cicd-prerelease/eitco/cicd/nexus-test:0.0.1-draft   # Status draft
```

* Referenz = Push-Ziel `<registry>/<repository>/<name>:<tag>`, Tag nach Abschnitt 6 – es gibt **kein `latest`**,
  die Version muss also immer angegeben werden.
* skillctl 0.7 entpackt Dateien **ohne Executable-Bits** (auch bei eigenen Images) – Skripte im Skill per
  `bash scripts/…` aufrufen lassen oder nach dem Pull `chmod +x` setzen.
* Unter Windows vertraut skillctl nur dem Zertifikatsspeicher des Systems – eine interne CA muss dort installiert sein.
* `npx skills` installiert aus dem Git-Repository von `git-deploy`, siehe Abschnitt 9.

## 14. Image-Format (Kurzfassung)

* Ein `tar+gzip`-Layer mit allen ausgewählten Dateien (generierte `SKILL.md` und `skill.yaml` statt der Quellen).
* Normalisiert: Einträge nach UTF-8-Pfad sortiert, uid/gid `0`, Mode `0644` (`0755` für Verzeichnisse
  und Executables), alle Zeitstempel = **Startzeit des Maven-Builds**. Jeder Build erzeugt daher neue
  Digests – die Images sind nicht reproduzierbar.
* Config `linux/amd64`; Manifest-Annotationen u. a. `io.skillimage.status`, `io.skillimage.wordcount`,
  `org.opencontainers.image.{created,description,title,vendor,version}`.

## 15. Stolpersteine

* **`<extensions>true</extensions>` vergessen** → Maven kennt das Packaging `skill` nicht.
* **Alte Koordinaten** `de.eitco.cicd.ociskills:oci-skills-maven-plugin` / Prefix `oci-skills` → seit 0.0.5
  `de.eitco.cicd.agentskills:agent-skills-maven-plugin` / `agent-skills`.
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
* **git-deploy tut nichts** → `gitUrl` fehlt, Skill hat nicht Status `published` (`<gitStatuses>`), oder
  SNAPSHOT-Build ohne `gitSnapshotBranch`.
* **`npx skills` ignoriert `#<tag>`** → auf selbstgehosteten Git-Servern muss die URL auf `.git` enden.
* **ssh-URL für `gitUrl`** → nicht unterstützt, `https` (mit `gitServerId`) oder `file` verwenden.
