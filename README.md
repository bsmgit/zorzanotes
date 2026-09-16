# Zorza Notes

**Your thoughts belong to you.**

Zorza Notes is a private, local-first, open-source desktop notes application built for people who want a simple place to write without turning their thoughts into somebody else's data.

There is no mandatory account, no required cloud service, and no internet connection required to use your notebook.

Your notes live on your computer.

🌅 **Zorza** means *dawn* — a fitting name for a quiet place to begin writing.

---

## Features

### 📝 Notebooks and Notes

Organize your writing into notebooks and create as many individual notes as you need.

Zorza is designed around a straightforward three-pane interface:

* Notebooks
* Notes
* Editor

No complicated workspace hierarchy is required just to write something down.

### 🔎 Full-Text Search

Search across all of your notes from one search box.

Zorza uses SQLite **FTS5** for fast local full-text search.

Search happens against your local database. Your notes do not need to be sent to a remote search service.

### 💾 Automatic Saving

Zorza automatically saves your work while you type.

There is no need to constantly reach for a Save button.

Notes are also saved when switching between notes, switching notebooks, or closing the application.

### # Markdown Friendly

Zorza keeps your writing portable.

Notes can be exported as ordinary Markdown files, allowing your writing to remain usable outside of Zorza.

Markdown directories can also be imported into Zorza as notebooks.

Your notes should never be trapped inside one application.

### 🔗 Clickable Web Links

Web addresses contained in notes are automatically detected and can be opened in your default web browser.

### ◐ Light and Dark Modes

Zorza includes both light and dark application themes.

Your preference is stored locally and restored the next time the application starts.

### Aa Accessibility and Text Sizing

Application text can be displayed at multiple sizes:

* Normal
* Large
* Extra Large

The editor also provides independent font family and font size controls for comfortable writing.

### ✍️ Writing Tools

The editor includes useful everyday writing features such as:

* Bulleted lists
* Numbered lists
* Automatic list continuation
* Undo
* Redo
* Font selection
* Font sizing
* URL detection

---

# Privacy by Design

## Your Computer. Your Database. Your Notes.

Zorza Notes is designed as a **local-first desktop application**.

Your notebook is stored locally rather than requiring a Zorza cloud account.

Zorza's design principles are simple:

* No mandatory account
* No mandatory cloud service
* No required internet connection
* No telemetry required to use the application
* Local SQLite storage
* Portable Markdown export
* User-owned data

Zorza should continue working even if the Zorza website disappears tomorrow.

That is intentional.

---

# Local Storage

Zorza stores application data in a local SQLite database named:

`zorza.db`

The database is kept in the operating system's normal application-data location.

### macOS

`~/Library/Application Support/Zorza Notes/zorza.db`

### Windows

`%LOCALAPPDATA%\Zorza Notes\zorza.db`

### Linux

`~/.local/share/zorza-notes/zorza.db`

If `XDG_DATA_HOME` is configured on Linux, Zorza uses that location instead.

---

# Encryption

Encryption at rest is an important part of the Zorza architecture.

Zorza uses an encryption-capable SQLite JDBC implementation, and encrypted SQLite database creation, writing, reopening, and incorrect-password rejection have been successfully tested during development.

However, **the primary Zorza Notes database is not yet migrated to encrypted storage**.

The encryption migration is being developed carefully so that existing user data is not put at risk.

The planned encrypted-storage system includes:

* Password-protected encrypted SQLite storage
* Encryption and decryption performed locally
* No password stored in the notes database
* No password transmitted to a Zorza server
* Safe migration of existing plaintext databases
* Preservation of note and notebook identifiers
* Migration verification before replacement
* A plaintext safety backup during migration

Until that migration is complete and verified, Zorza does **not** claim that existing `zorza.db` databases are encrypted at rest.

---

# Offline by Default

Zorza does not require an internet connection to:

* Create notebooks
* Create notes
* Edit notes
* Save notes
* Search notes
* Organize notes
* Import Markdown
* Export Markdown

The core notebook remains available wherever your computer is available.

---

# Markdown Import and Export

Data portability is a core Zorza principle.

## Export

Zorza can export notebooks and notes into a normal directory structure containing Markdown files.

This gives you a human-readable copy of your writing that can be opened by:

* Text editors
* Markdown editors
* IDEs
* Other notes applications
* Future software that does not know anything about Zorza

## Import

A directory containing Markdown documents can be imported into Zorza as a notebook.

Zorza attempts to use the first Markdown heading as the note title when appropriate.

---

# Technology

Zorza Notes is intentionally built with a relatively simple desktop architecture.

### Language

Java 25 LTS

### Desktop UI

JavaFX / OpenJFX

### Database

SQLite

### Database Access

JDBC and raw SQL

Zorza deliberately avoids requiring a heavy ORM layer.

### Search

SQLite FTS5

### Build System

Apache Maven

### Packaging

Java `jpackage`

Native application packages include the Java runtime required to run Zorza, so end users do not need to separately install Java.

---

# Architecture

The project follows a simple separation of responsibilities.

Typical components include:

```text
ZorzaNotes
    │
    ├── UI / JavaFX
    │
    ├── MarkdownEditor
    │
    ├── AppSettings
    │
    ├── ThemeManager
    │
    ├── ExportImportService
    │
    ├── NotebookRepository
    │
    ├── NoteRepository
    │
    └── Database
            │
            └── SQLite
```

The goal is not to build the most elaborate architecture possible.

The goal is to keep Zorza understandable, maintainable, and dependable.

---

# Requirements for Development

To build Zorza Notes from source, you will need:

* JDK 25
* Apache Maven
* Git

Temurin JDK 25 is a good choice for development.

---

# Clone the Repository

```bash
git clone https://github.com/bsmgit/zorzanotes.git
cd zorzanotes
```

---

# Build Zorza Notes

Build the project with Maven:

```bash
mvn clean package
```

Maven will compile Zorza and retrieve the required dependencies.

---

# Run from Source

The application can be run through the JavaFX Maven plugin:

```bash
mvn clean javafx:run
```

---

# IntelliJ IDEA

Zorza can also be developed directly in IntelliJ IDEA.

1. Clone the repository.
2. Open the repository root in IntelliJ IDEA.
3. Allow IntelliJ to import the Maven project.
4. Configure JDK 25 as the project SDK.
5. Open the Maven tool window.
6. Navigate to the JavaFX Maven plugin.
7. Run `javafx:run`.

The main Java package is:

```text
org.zorzanotes
```

---

# Project Structure

A typical source tree looks like:

```text
zorzanotes/
├── pom.xml
├── README.md
├── LICENSE
│
├── src/
│   └── main/
│       ├── java/
│       │   └── org/
│       │       └── zorzanotes/
│       │           ├── ZorzaLauncher.java
│       │           ├── ZorzaNotes.java
│       │           ├── Database.java
│       │           ├── Notebook.java
│       │           ├── Note.java
│       │           ├── NotebookRepository.java
│       │           ├── NoteRepository.java
│       │           ├── MarkdownEditor.java
│       │           ├── ExportImportService.java
│       │           ├── AppSettings.java
│       │           └── ThemeManager.java
│       │
│       └── resources/
│
├── packaging/
│   ├── mac/
│   │   ├── build-dmg.sh
│   │   ├── zorza-icon.png
│   │   ├── Zorza.icns
│   │   └── resources/
│   │
│   └── windows/
│       ├── build-msi.ps1
│       └── Zorza.ico
│
└── releases/
```

The exact contents may change as development continues.

---

# macOS Build

Zorza uses `jpackage` to create a self-contained macOS application and DMG installer.

From the repository root:

```bash
./packaging/mac/build-dmg.sh
```

The completed installer is placed in:

```text
releases/
```

The macOS package includes the runtime required by Zorza.

Users do not need to install Java separately.

---

# Windows Build

Windows packages must be built on Windows so that the application contains the correct Windows JavaFX native components.

From PowerShell:

```powershell
.\packaging\windows\build-msi.ps1
```

The resulting MSI is placed in:

```text
releases/
```

Do not build the Maven package on macOS and copy `target/package` to Windows. Run the Maven build on the target operating system so Maven obtains the appropriate native JavaFX components.

---

# Native Packaging

Zorza uses Java's standard `jpackage` tooling to create native installers.

Current packaging targets include:

### macOS

* `.app`
* `.dmg`

### Windows

* `.msi`

Linux packaging is planned as the project develops.

---

# Application Version

The current release is:

**Zorza Notes 1.0.0**

The version is displayed from within the application's About window.

---

# About Zorza Notes

Inside the application, choose:

**Help → About Zorza Notes**

The About window displays:

* Zorza Notes
* “Your thoughts belong to you.”
* Application version
* Zorza Notes website
* MIT License information

Official website:

https://zorzanotes.com

---

# Source Code

The Zorza Notes source code is available on GitHub:

https://github.com/bsmgit/zorzanotes

Clone URL:

```text
https://github.com/bsmgit/zorzanotes.git
```

---

# Contributing

Contributions are welcome.

If you would like to improve Zorza Notes:

1. Fork the repository.
2. Create a branch for your change.
3. Make your changes.
4. Test the application.
5. Submit a pull request.

When contributing, try to preserve the philosophy of the project:

**Keep it simple. Keep it local. Keep the user's data theirs.**

Large dependencies or architectural changes should have a clear reason for existing.

---

# Development Philosophy

Zorza is intentionally opinionated about a few things.

## Local First

The desktop application should remain useful without a network connection.

## User-Owned Data

A user should always have a reasonable path to get their writing out of Zorza.

## Simple Storage

SQLite provides a mature, portable, understandable storage layer without requiring a database server.

## Open Formats

Markdown provides a simple escape hatch from the application.

## No Unnecessary Infrastructure

Zorza should not require a server merely because modern software commonly has one.

## Privacy Should Be Architectural

Privacy is strongest when the application simply does not need to collect the information in the first place.

## Dependable Over Clever

Zorza favors understandable technology and straightforward code over unnecessary complexity.

---

# Roadmap

Development areas include:

* Encrypted `zorza.db` storage
* Safe plaintext-to-encrypted database migration
* Password-based vault unlocking
* Additional Markdown editing improvements
* Packaging improvements
* macOS signing and notarization
* Windows code signing
* Linux packaging
* Additional accessibility improvements
* Continued import/export improvements
* Database schema versioning and migrations

Features on the roadmap are not promises or descriptions of functionality already present in a released build.

---

# Security

Zorza's security model is still being developed.

If you discover a security problem, please avoid publishing sensitive exploit details until the issue can be investigated and corrected.

The project should rely on established cryptographic implementations rather than custom cryptography.

Encryption-related changes should be treated carefully, particularly when they involve migration of an existing user's notes.

---

# Data Backups

Zorza is local-first, which also means users should maintain backups of important data.

Markdown export provides one portable backup option.

Users may also back up the Zorza application-data directory containing `zorza.db`.

Before performing database migrations or other potentially destructive development work, make a backup.

---

# Why Zorza?

Many modern applications begin with a login screen.

Zorza begins with a notebook.

There is value in software that simply runs on your computer, opens your files, stores your work locally, and does not require an ongoing relationship with a remote service.

Zorza Notes is an attempt to build that kind of software.

No mandatory account.

No mandatory cloud.

No subscription required to access your own thoughts.

No proprietary format holding your writing hostage.

Just a notebook.

**Your thoughts belong to you.**

---

# License

Zorza Notes is released under the **MIT License**.

```text
MIT License

Copyright (c) 2026 Zorza

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

A copy of the license should also be included in the repository as `LICENSE`.

---

# Links

**Website:** https://zorzanotes.com

**GitHub:** https://github.com/bsmgit/zorzanotes

**Clone:**

```bash
git clone https://github.com/bsmgit/zorzanotes.git
```

---

<p align="center">
<strong>Zorza Notes</strong><br>
<em>Your thoughts belong to you.</em><br><br>
Open source · Local first · MIT licensed
</p>
