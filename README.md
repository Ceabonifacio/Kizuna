# Kizuna

Kizuna is a local JavaFX desktop app for Mindanao Kokusai Daigaku students to explore clubs and events, complete a preference assessment, apply to clubs, and manage applications for clubs where they hold an active leadership membership. SQLite stores the application data on this computer. It does not synchronize between computers.

## Requirements

- JDK 25 or later
- Internet access the first time you build or run the app so Maven can download the build tools and dependencies

OpenJFX 27 requires JDK 25 or later. The Maven Wrapper downloads the pinned Maven version, and Maven downloads JavaFX 27, SQLite JDBC, and JUnit Jupiter. You do not need to install Maven or a separate JavaFX SDK.

## Run and test

Install a JDK 25 or later, open a terminal in the project folder, and check `java -version` and `javac -version`. Then use the commands for your operating system.

**macOS (Terminal):**

```sh
./mvnw clean test
./mvnw javafx:run
```

**Windows (PowerShell):**

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd javafx:run
```

The first wrapper run downloads Maven 3.10.0 into your local Maven cache. In VS Code, use **Terminal → Run Task → Run Kizuna (JavaFX)**; the task selects `mvnw` on macOS and `mvnw.cmd` on Windows. Do not use a single-file Java runner or Code Runner for `KizunaApp.java`; those launch Java without the JavaFX runtime modules. The recommended **Extension Pack for Java** helps with editing and debugging, but is not required to run the Maven task.

## Local data and backups

Kizuna stores its database and copied profile photos in a per-user `.kizuna` folder, independent of the terminal's working directory:

- macOS: `~/.kizuna/kizuna.db` and `~/.kizuna/avatars/`
- Windows: `%USERPROFILE%\.kizuna\kizuna.db` and `%USERPROFILE%\.kizuna\avatars\`

Back up the complete `.kizuna` folder while Kizuna is closed. This preserves the SQLite database and the profile photos it references.

For a **local development reset only**, close Kizuna, make a backup if any data matters, then remove the `.kizuna` folder from your home directory (`$HOME/.kizuna` on macOS or `%USERPROFILE%\.kizuna` on Windows). The next launch creates a fresh database and demo data. Do not use this reset step for real student information.

## Local development demo accounts

**LOCAL DEVELOPMENT ONLY. Do not use these credentials for real student records.** All seeded accounts use the same demo password:

```text
KizunaDemo25!
```

Leadership usernames:

```text
kaminari.president   kaminari.vp
peer.president       peer.vp
ichiban.president    ichiban.vp
tsuushin.president   tsuushin.vp
kendo.president      kendo.vp
redcross.president   redcross.vp
```

Fictional member accounts for demonstrations:

```text
student.demo
student.mina
student.kai
```

Additional seeded member usernames follow the `club.member.1` through `club.member.3` pattern (for example, `kaminari.member.1`).

Each password is stored as a unique salted PBKDF2 hash; the demo password is not stored in SQLite. Seed logic creates missing accounts and memberships only. It does not reset existing passwords or overwrite profile data.

## Implementation notes

- The app uses JavaFX views → service layer → repositories → SQLite. Repositories receive the same JDBC connection during multi-step transactions.
- SQLite schema version 2 migrates version 1 and uses foreign keys, checks, unique and partial indexes, and triggers that require applications to start as `PENDING` and follow the allowed state transitions. Every connection enables foreign keys and a 5-second busy timeout.
- Passwords use `PBKDF2WithHmacSHA256` with 600,000 rounds, random 16-byte salts, and 32-byte derived keys.
- The Kizuna logo stays in `src/main/resources/images/KizunaLogo.svg`; JSVG rasterizes it from the classpath into a transparent high-resolution JavaFX image.
- Recommendations use a typed adjacency list and an actual breadth-first traversal of `Student → Interest → Club`; shared interest weights determine the score. All six seeded clubs receive a stored score, including zero-score fallbacks.
- The club network and hierarchy are built from active memberships. Application workflows use service authorization and a transaction for status changes, history, and membership acceptance.

