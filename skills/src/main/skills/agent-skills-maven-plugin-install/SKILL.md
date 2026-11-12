---
name: agent-skills-maven-plugin-install
description: >-
  Erklärt, wie ein Projekt mit dem agent-skills-maven-plugin (de.eitco.cicd.agentskills:agent-skills-maven-plugin,
  Prefix agent-skills) veröffentlichte Agent-Skills aus Maven-Repositories oder OCI-Registries nach
  .claude/skills installiert, statt sie einzuchecken. Heranziehen, sobald die Goals agent-skills:sync,
  agent-skills:update oder agent-skills:pull verwendet werden, `<sources>` (type maven/oci) oder
  `<skills>` mit id, version, status, repository, source bzw. direkter Lage (image, groupId/artifactId)
  konfiguriert werden, Platzhalter wie {name[1:]} oder {repository.parent} in groupId/artifactId/
  classifier/repository auftauchen, skills.lock.yaml oder .oci-skill.json gelesen, committet oder
  repariert werden, oder ein Fehler beim Auflösen/Installieren untersucht wird (Skill nicht gefunden,
  Digest weicht ab, Namenskollision, ungültiger Lock-File). Das Bauen und Veröffentlichen von Skills
  (packaging skill, push, git-deploy) beschreibt agent-skills-maven-plugin.
---

# Skills installieren mit dem agent-skills-maven-plugin

Projekte, die Skills **nutzen**, müssen sie nicht einchecken: `agent-skills:sync` installiert sie aus
Maven-Repositories oder OCI-Registries nach `.claude/skills/<name>/` und hält die Versionen in
`skills.lock.yaml` fest. Wie Skills gebaut und veröffentlicht werden (Packaging `skill`, `push`, Skill-Jars,
`git-deploy`), steht in [[agent-skills-maven-plugin]].

| Goal     | Zweck                                                                   | Lock-File                 |
|----------|-------------------------------------------------------------------------|---------------------------|
| `sync`   | installiert alle `<skills>`, bevorzugt gelockte Versionen               | liest und schreibt        |
| `update` | wie `sync`, löst aber neu auf (`-Dskill=…` beschränkt auf einzelne)     | schreibt neu              |
| `pull`   | installiert einen Skill ad hoc von der Kommandozeile                    | wird nicht geschrieben    |

---

## 1. Schnellstart

```xml
<plugin>
    <groupId>de.eitco.cicd.agentskills</groupId>
    <artifactId>agent-skills-maven-plugin</artifactId>
    <version>${agent-skills-maven-plugin.version}</version>
    <configuration>
        <sources>
            <source>
                <id>my-maven</id>
                <type>maven</type>
                <groupId>com.example.{repository.parent}</groupId>
                <artifactId>{repository.last}</artifactId>
            </source>
            <source>
                <id>my-registry</id>
                <type>oci</type>
                <registry>registry.example.com</registry>
                <registryServerId>my-registry</registryServerId>
            </source>
        </sources>
        <skills>
            <skill>
                <id>acme/nexus-test</id>
                <version>[1.0,2.0)</version>
                <repository>teams/acme/skills</repository>
            </skill>
            <skill>
                <id>acme/release-notes</id>
                <version>2.1.0</version>
                <status>testing</status>
                <repository>products/notes/skills</repository>
            </skill>
        </skills>
    </configuration>
    <executions>
        <execution>
            <goals><goal>sync</goal></goals>   <!-- default phase initialize -->
        </execution>
    </executions>
</plugin>
```

`sync` ist an **keinen** Lifecycle gebunden – erst die Execution lässt ihn bei jedem Build laufen.
`skills.lock.yaml` committen, `.claude/skills/<name>/` wird nie eingecheckt (siehe Abschnitt 7).

## 2. Skill-ID, Repository und Sources

* Ein Skill wird über **`<namespace>/<name>`** seiner SkillCard identifiziert – unabhängig vom Speicherort.
* `<repository>` des `<skill>` ist der OCI-Repository-Pfad, unter den er gepusht wurde. Dadurch deckt eine
  einzige Source eine ganze Registry bzw. ein ganzes Maven-Repository ab.
* Jede Source bildet ID + Repository auf eigene Koordinaten ab:

| `type`  | der Skill ist                                                                | erzeugt von            |
|---------|------------------------------------------------------------------------------|------------------------|
| `maven` | Artefakt `<groupId>:<artifactId>:jar:<classifier>[-<status>]:<version>`      | `agent-skills:archive` |
| `oci`   | Image `<registry>/<repository>/<name>:<version>[-<status>]`                   | `agent-skills:push`    |

`<source>`-Elemente:

| Element            | `maven`                | `oci`                                         |
|--------------------|------------------------|-----------------------------------------------|
| `id`               | Pflicht, eindeutig     | Pflicht, eindeutig                            |
| `groupId`          | Pflicht (Template)     | –                                             |
| `artifactId`       | Pflicht (Template)     | –                                             |
| `classifier`       | Template, default `{name}` | –                                         |
| `registry`         | –                      | Pflicht                                       |
| `repository`       | –                      | optional, Template für Skills ohne `<repository>` |
| `registryServerId` | –                      | `<server>` der `settings.xml`                 |
| `caCertificates`, `allowPlainHttp` | –      | zusätzliche CAs bzw. ohne TLS (nur Test)      |

* Maven-Sources nutzen Repositories, Mirrors und Credentials des Builds, inkl. lokalem Repository und Reactor.
* OCI-Sources: Die Versionen kommen aus den **Tags** von `<registry>/<repository>/<name>` – die Credentials
  müssen Tags auflisten dürfen. Bei OCI-Sources wird das `<repository>` des Skills **unverändert** verwendet.
* Classifier bekommt bei Status ≠ `published` das Suffix `-<status>` (wie der Tag des Images).

## 3. Platzhalter in Templates

Gelten in `groupId`, `artifactId`, `classifier` (maven) und `repository` (oci). Beispiel für
`acme/nexus-admin-test` mit Repository `teams/acme/skills`:

| Platzhalter                                  | Wert                     | Bedeutung                              |
|----------------------------------------------|--------------------------|----------------------------------------|
| `{namespace}`                                | `acme`                   | aus der Skill-ID                       |
| `{name}`                                     | `nexus-admin-test`       | aus der Skill-ID                       |
| `{name[0]}`, `{name[1]}`, `{name[-1]}`       | `nexus`, `admin`, `test` | einzelner Teil                         |
| `{name[1:]}`                                 | `admin-test`             | ohne ersten Teil                       |
| `{name[:-1]}`                                | `nexus-admin`            | ohne letzten Teil                      |
| `{name[0:2]}`                                | `nexus-admin`            | Teile 0 und 1                          |
| `{name[:0]}`                                 | leer                     | kein Teil                              |
| `{namespace[…]}`                             |                          | wie `{name[…]}`                        |
| `{repository}`                               | `teams.acme.skills`      | `/` durch `.` ersetzt                  |
| `{repository[1:]}`                           | `acme.skills`            | wie `{name[…]}`                        |
| `{repository.parent}` = `{repository[:-1]}`  | `teams.acme`             | ohne letztes Segment                   |
| `{repository.last}` = `{repository[-1]}`     | `skills`                 | letztes Segment                        |

* Namespace/Name werden an `-` geteilt und mit `-` verbunden, das Repository an `/` geteilt und mit `.` verbunden.
* Indizes wie in Python: ab 0, negativ von hinten, `[from:to]` mit exklusivem Ende, Grenzen weglassbar.
* Ein **einzelner** Index, den es nicht gibt, ist ein Fehler; Slices werden gekappt und dürfen leer sein.
* Doppelte Punkte sowie Punkte/Bindestriche am Anfang oder Ende fallen weg: `com.example.{repository.parent}`
  mit Repository `skills` ergibt `com.example`.
* Unbekannte Platzhalter sind ein Konfigurationsfehler.

Mit dem Schnellstart ist `acme/nexus-admin-test` das Artefakt `com.example.teams.acme:skills:jar:nexus-admin-test`
bzw. das Image `registry.example.com/teams/acme/skills/nexus-admin-test`.

## 4. Muster

**Name aufteilen** – `acme/tools-delta` als `com.example.acme:tools:jar:delta`:

```xml
<source>
    <id>my-tools</id>
    <type>maven</type>
    <groupId>com.example.{namespace}</groupId>
    <artifactId>{name[0]}</artifactId>
    <classifier>{name[1:]}</classifier>
</source>
```

**Ein Skill pro Projekt (Hauptartefakt)** – ein leerer Classifier wählt das Hauptartefakt. `<classifier/>` ist in
Maven kein Wert, daher `{name[:0]}`. Das Hauptartefakt hat keinen Status in den Koordinaten; es wird nur
genommen, wenn der Skill darin den angeforderten Status hat. `acme/solo-epsilon` als `com.example.acme:epsilon:jar`:

```xml
<source>
    <id>my-projects</id>
    <type>maven</type>
    <groupId>com.example.{namespace}</groupId>
    <artifactId>{name[1:]}</artifactId>
    <classifier>{name[:0]}</classifier>
</source>
```

**Default-Repository** – es gibt keinen Plugin-Parameter dafür:

* *Pro Source*: Templates ohne Repository-Platzhalter brauchen kein `<repository>` – feste Werte direkt ins
  Template schreiben, z. B. OCI `<repository>teams/{namespace}/skills</repository>` oder Maven
  `<artifactId>skills</artifactId>`.
* *Für alle Skills*: eine (erbbare) Property, z. B. `<skills.repository>teams/acme/skills</skills.repository>`, und
  in jedem `<skill>` `<repository>${skills.repository}</repository>`. Eine Änderung löst neu auf.
* **Nicht** `<repository>` in die Plugin-`<configuration>` schreiben – das ist der Parameter von `push`/`pull`
  und würde das Push-Ziel mit setzen.

## 5. Direkte Lage statt Sources

Ein `<skill>` kann seinen Speicherort selbst nennen – dann werden nur dort Versionen gesucht und es braucht gar
keine `<sources>`:

```xml
<skill>
    <id>acme/nexus-test</id>
    <version>[1.0,2.0)</version>
    <image>registry.example.com/teams/acme/skills/nexus-test</image>   <!-- Tags <version>[-<status>] -->
</skill>
<skill>
    <id>acme/release-notes</id>
    <version>2.1.0</version>
    <groupId>com.example.acme</groupId>                                <!-- …:acme-skills:jar:release-notes:2.1.0 -->
    <artifactId>acme-skills</artifactId>
</skill>
```

* `<image>` = `<registry>/<repository>`, wörtlich, **ohne Tag oder Digest**. `registryServerId`,
  `allowPlainHttp`, `caCertificates` des `<skill>` haben Vorrang; fehlende Werte kommen von der ersten
  `oci`-Source mit derselben Registry, sonst anonym mit den CAs der JVM.
* `<groupId>`+`<artifactId>` gehören zusammen, `<classifier>` default `{name}` (mit Status-Suffix).
  Platzhalter der Skill-ID sind erlaubt, Repository-Platzhalter nicht.
* `id` und `version` bleiben Pflicht; das Artefakt/Image muss den Skill `id` enthalten.
* Nicht kombinierbar: direkte Lage mit `<source>`/`<repository>`, `<image>` mit `<groupId>`/`<artifactId>`.
  `registryServerId`/`allowPlainHttp`/`caCertificates` brauchen `<image>`, `classifier` braucht `<groupId>`.

## 6. Auflösen und Lock-File

* `version`: Maven-Range oder exakte Version – anders als in Maven passt `1.2.0` **nur** auf `1.2.0`.
* `status`: die **am wenigsten reife** akzeptierte Stufe, default `published` (`testing` akzeptiert also
  `testing` und `published`). Bei mehreren Status einer Version gewinnt der reifste.
* Sources werden in ihrer Reihenfolge gefragt, die erste mit passender Version gewinnt; `<source>` im
  `<skill>` fragt nur diese. Genommen wird die höchste passende Version.
* Namespace und Name der Image-Annotationen müssen zur `id` passen.

`skills.lock.yaml` neben der POM – **committen**:

```yaml
# generated by agent-skills-maven-plugin - commit it, update it with mvn agent-skills:update
skills:
- id: acme/nexus-test
  requested:
    version: "[1.0,2.0)"
    status: published
    repository: teams/acme/skills
  version: 1.1.0
  status: published
  digest: sha256:4f2a...
  source: my-maven
```

* Solange sich der Request (`version`, `status`, `repository`, `location`, `source`) nicht ändert, installiert
  `sync` **genau** das gelockte Image. Neuere Versionen nur per `update`.
* Ein gelocktes Image darf aus **jeder** Source kommen (Jar und Image desselben Builds haben denselben Digest);
  zuerst wird die Source gefragt, aus der aufgelöst wurde.
* Liefert eine Source einen **anderen Digest**, schlägt der Build fehl → bei bewusstem Rebuild `agent-skills:update`.
* Bei direkter Lage steht `requested.location` im Lock-File (`<groupId>:<artifactId>[:<classifier>]` bzw.
  `<registry>/<repository>`), ebenso als `source`.
* Einträge sortiert nach id; die Datei wird nur bei inhaltlicher Änderung geschrieben.
* Ungültiger Lock-File → Build schlägt fehl; reparieren oder löschen und `agent-skills:update` ausführen.

## 7. Installierte Skills

* Ziel: `.claude/skills/<name>/`, markiert mit `.oci-skill.json` (`id`, `version`, `status`, `digest`,
  `source`). Nur markierte Verzeichnisse werden ersetzt oder entfernt – **handgeschriebene Skills** können
  daneben liegen; ein angeforderter Skill mit dem Namen eines solchen Verzeichnisses ist ein Fehler.
* Ist der installierte Digest der gelockte, greift `sync` gar nicht auf die Sources zu (schnell, offline-fähig).
* Nicht mehr angeforderte Skills aus dem Lock-File werden entfernt.
* Jeder installierte Skill enthält eine `.gitignore` mit `*`; `.claude/skills/.gitignore` bleibt unangetastet.
* Derselbe Skill doppelt angefordert oder zwei Skills gleichen Namens aus verschiedenen Namespaces → Fehler.

## 8. `update` und `pull`

```shell
mvn agent-skills:update                              # alle Skills neu auflösen
mvn agent-skills:update -Dskill=nexus-test,acme/foo  # nur diese (IDs oder Namen, kommagetrennt)

mvn agent-skills:pull -Dskill=acme/nexus-test             # höchste Version aus den <sources>
mvn agent-skills:pull -Dskill=acme/nexus-test:1.2.0 -Dskill.pull.repository=teams/acme/skills
mvn agent-skills:pull -Dskill.artifact=com.example.acme:acme-skills:1.2.0:nexus-test
mvn agent-skills:pull -Dskill.artifact=com.example.acme:epsilon:1.2.0   # Hauptartefakt mit einem Skill
mvn agent-skills:pull -Dskill.image=registry.example.com/skills/acme/nexus-test:1.2.0
```

* `update` prüft Maven-Sources unabhängig von ihrer Update-Policy (wie `-U`). Ein nicht angeforderter Skill
  in `-Dskill` ist ein Fehler.
* `pull`: **genau eins** von `skill`, `artifact`, `image`. Kein Lock-File-Eintrag – `sync` lässt den Skill aber stehen.
* `-Dskill` braucht `<sources>` und damit ein Projekt; `-Dskill.artifact` und `-Dskill.image` gehen **ohne Projekt**.
* `-Dskill.image` vertraut nur den CAs der JVM (`caCertificates` nicht unterstützt), Tag oder `@<digest>` angeben.

## 9. Parameter-Referenz

`sync` / `update` (default phase `initialize`, nicht gebunden):

| Parameter          | Property                 | Default                                 |
|--------------------|--------------------------|-----------------------------------------|
| `sources`          |                          |                                         |
| `skills`           |                          |                                         |
| `installDirectory` | `skill.installDirectory` | `${project.basedir}/.claude/skills`     |
| `lockFile`         | `skill.lockFile`         | `${project.basedir}/skills.lock.yaml`   |
| `workDirectory`    |                          | `${project.build.directory}/skill-pull` (danach gelöscht) |
| `skip`             | `skill.sync.skip`        | `false`                                 |
| `skill` (nur `update`) | `skill`              | – (alle)                                |

`<skill>`: `id`, `version`, `status`, `source`, `repository` – oder direkte Lage `image` (+ `registryServerId`,
`allowPlainHttp`, `caCertificates`) bzw. `groupId`, `artifactId`, `classifier`.

`pull` (zusätzlich `sources`, `installDirectory`):

| Parameter          | Property                    | Default     | Beschreibung                                         |
|--------------------|-----------------------------|-------------|------------------------------------------------------|
| `skill`            | `skill`                     |             | `<namespace>/<name>[:<version>]`, Version auch als Range |
| `status`           | `skill.status`              | `published` | mit `skill`: minimal akzeptierter Status             |
| `source`           | `skill.source`              |             | mit `skill`: einzige gefragte Source                 |
| `repository`       | `skill.pull.repository`     |             | mit `skill`: Repository des Skills                   |
| `artifact`         | `skill.artifact`            |             | `<groupId>:<artifactId>:<version>[:<classifier>]`    |
| `image`            | `skill.image`               |             | `<registry>/<repository>:<tag>` oder `…@<digest>`    |
| `registryServerId` | `skill.registryServerId`    |             | mit `image` (gleiche Property wie bei `push`)        |
| `allowPlainHttp`   | `skill.pull.allowPlainHttp` | `false`     | mit `image`, nur für Test-Registries                 |

## 10. Stolpersteine

* **Skill nicht gefunden** → Template expandiert zu leerem `groupId`/`artifactId`, Repository-Platzhalter bei
  Skill ohne `<repository>` oder OCI-Source ohne `<repository>`. Skill per `<source>` an eine passende Source
  binden oder direkte Lage verwenden.
* **`<classifier/>`** wählt nicht das Hauptartefakt – `{name[:0]}` schreiben.
* **Exakte Version** `1.2.0` ist kein „mindestens 1.2.0“ – für Bereiche `[1.2.0,)` verwenden.
* **Digest weicht ab** → der Skill wurde mit gleicher Version neu gebaut; `agent-skills:update -Dskill=<name>`.
* **OCI-Source findet keine Versionen** → Credentials dürfen keine Tags auflisten oder falsches `<repository>`.
* **`<repository>` auf Plugin-Ebene** setzt auch das Push-Ziel – nur pro `<skill>`/`<source>` setzen.
* **Artefakte von 0.0.2** (Tar statt Jar) lassen sich nicht installieren; Jars von 0.0.3/0.0.4
  (`META-INF/oci-skills/`) schon.
* **Verzeichnis existiert schon ohne `.oci-skill.json`** → handgeschriebener Skill gleichen Namens; umbenennen.
* **`sync` läuft nicht** → keine Execution gebunden oder `-Dskill.sync.skip=true` gesetzt.
