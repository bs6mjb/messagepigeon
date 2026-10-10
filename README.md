# MessagePigeon

A desktop tool for browsing and editing messages on IBM MQ queues, written in Java (Swing). Connect to one or more
queue managers, look at what is on a queue without removing anything, edit or delete messages, add new ones, and back
queues up to plain-text files you can read in any editor.

> IBM and MQ are trademarks of International Business Machines Corporation. This project is independent and is not
> affiliated with or endorsed by IBM.

## Features

- **Connection profiles** in a sidebar, with TLS 1.2 / 1.3, client certificates, and PKCS12, JKS or JCEKS key and
  trust stores. Passwords are never stored in plain text.
- **One tab per queue**, so several queues (on one or several queue managers) can be open at once.
- **Non-destructive browsing** in a sortable grid, with a viewer for the highlighted message as Text, XML, JSON, a
  tree, or hex.
- **Message window** (double-click a message): all headers, the data in the same views, and editing with Save back to
  the queue.
- **Add messages**: type one, add one per line of a text file, or one per file in a folder.
- **Full or selective export and import** to a human-readable text format.
- **Delete** selected messages, or empty a queue (typing the queue name to confirm).
- Icon bar, menus with icons and shortcuts, built-in help (F1).
- A Windows launcher (`MessagePigeon.exe`) that finds a suitable JDK, or asks for one.

## Requirements

- JDK 21 or later (to build and to run).
- [Maven](https://maven.apache.org/) to build. The build downloads IBM's MQ client library (`com.ibm.mq.allclient`)
  from Maven Central only to compile against it; it is **not** put in the jar.
- **Your own copy of IBM's MQ client library** (`com.ibm.mq.allclient.jar`) to run the program. See
  [The IBM MQ client library](#the-ibm-mq-client-library).
- Windows for the `.exe` launcher and the best look and feel; the jar itself is plain Java and runs wherever Java does.
- Docker, optionally, for the local test queue manager in `docker/`.

## Build and run

    mvn package
    java -jar target/messagepigeon.jar

The build produces a single runnable jar.

### Windows exe

    .\build-exe.ps1

builds `dist\MessagePigeon.exe` and `dist\messagepigeon.jar`. The script needs JDK 21+ (`JAVA_HOME`, or pass `-JavaHome`)
and Maven on the `PATH` (or pass `-MavenBin`). Copy the `dist` folder anywhere (the two files must stay together) and
run the exe. The launcher is compiled with the `csc.exe` that ships with Windows, so no extra SDK is needed.

On start-up the exe looks for a JDK (any Java 21 or later) in this order: the folder you chose last time, `JAVA_HOME`,
`PATH`, the registry entries the common installers write, then the usual install folders. A Java that is too old is
skipped. If none is found, it asks you to choose the JDK folder (the one `JAVA_HOME` would point to, for example
`C:\Program Files\Java\jdk-21`), checks it, remembers it in `~/.messagepigeon/java-path.txt`, and starts the app.
Delete that file to be asked again.

## The IBM MQ client library

IBM licenses its MQ client library itself, so MessagePigeon does not include it: you supply your own copy of
`com.ibm.mq.allclient.jar`. It is in the `java\lib` folder of an IBM MQ installation, in IBM's MQ redistributable
client, and on Maven Central; IBM's licence terms apply to whichever copy you use.

On start-up the program looks for it in this order, and uses the first valid one:

1. `-Dmessagepigeon.mqjar=<path>` or the `MESSAGEPIGEON_MQ_JAR` environment variable;
2. the file you chose last time (remembered in `~/.messagepigeon/mq-client-jar.txt`);
3. a `lib` folder (or the same folder) next to `messagepigeon.jar` / `MessagePigeon.exe`;
4. an IBM MQ installation (`MQ_JAVA_INSTALL_PATH`, `MQ_INSTALLATION_PATH`, `C:\Program Files\IBM\MQ\java\lib`,
   `/opt/mqm/java/lib`).

If none is found it explains what is needed and asks you to locate the file, checking that it really is IBM's client
before remembering it. **If the file you chose is later moved or deleted**, the next start says so and asks you to
locate it again. You can also change it at any time with **Help > IBM MQ Client Library...**; the change applies the
next time the program starts.

## A local test queue manager

`docker/` contains a ready-made IBM MQ developer queue manager with plain and TLS channels and some test queues, so
you can try everything safely. See [docker/README.md](docker/README.md).

## Using it

- The **sidebar** lists your connection profiles (green dot = connected). Right-click a profile for **Open Queue...**,
  Connect / Disconnect, Queue History, New, Edit, Duplicate and Delete. Double-click opens a queue.
- In a queue tab (the only control on the tab itself is **Max messages**; everything else is on the icon bar, the
  Queue menu or the right-click menu):
  - The grid is a non-destructive browse (`MQGMO_BROWSE`); click a column header to sort.
  - The viewer under the grid shows the highlighted message as Text, XML, JSON, Tree or Hex (Text by default; your
    choice carries from message to message, and XML/JSON/Tree are offered only when the content is valid).
  - **Double-click a message** to open it in the message window.
  - **Right-click** one or more selected rows to **Export Selected to File...** or **Delete Selected...**.
  - **Export to File...** writes every message on the queue to a file; **Import from File...** puts an export file back.
  - **Insert Message...** adds one message; **Add From File...** adds one message per non-blank line;
    **Add From Folder...** adds one message per file in a folder (whole files, so multi-line XML/JSON is fine),
    optionally limited to certain file extensions, and in file-name or date-modified order.
  - **Delete All...** needs the queue name typed to confirm.
- An **icon bar** under the menu bar gives one-click access to the common actions (hover for a tooltip and shortcut).
  Every menu item, including the right-click menus, has an icon; they are drawn in code (`MenuIcons.java`), so they
  stay sharp at any display scaling.
- **Help** (F1) is `src/main/resources/help/help.html`. It covers every feature, the keyboard shortcuts and common MQ
  error codes, and can be opened in a browser for printing.

### Message window

All headers (ids, put time, format, CCSID, reply-to, ...) and the data, with a **View as** selector:

| View | |
|---|---|
| Text | editable plain text |
| XML | indented, colour-coded (offered when the text is well-formed XML) |
| JSON | indented, colour-coded (offered when the text is valid JSON) |
| Tree | collapsible tree of the XML or JSON |
| Hex | hex dump |

The window always opens on Text. Edit the text, persistence, priority or reply-to, then **Save to Queue** or
**Put as New Message** (the original stays). Save removes the original and puts the edited message back in one unit of
work, so a failure changes nothing. It keeps the original Message ID, but MQ cannot update a message in place, so the
message re-joins the queue behind others of its priority. **Format text** re-indents XML or JSON in the editor. Binary
content is shown read-only.

### Export file format

Open an export in any text editor. Each message is a block of `name: value` header lines, a `---` line, the message
text exactly as it was on the queue, then an end line:

    === MESSAGE 1 ===
    format: MQSTR
    ccsid: 1208
    persistence: persistent
    priority: 5
    ...
    boundary: === END OF MESSAGE ===
    ---
    {"orderId": 1001, "amount": 249.99}
    === END OF MESSAGE ===

You can edit the text or the header values before importing. Lines marked `(info)` (message ID, put time, ...) are
ignored on import. Binary messages, or text that would not survive a decode and re-encode, are written as Base64 and
marked `data-encoding: base64`. If the text itself contains the end line, the writer lengthens the boundary so the file
still reads back correctly. Message IDs are not restored on import; MQ assigns new ones.

## Connections, TLS and password storage

Profiles hold host, port, queue manager, channel and user/password, saved in `~/.messagepigeon/`. TLS 1.2 / 1.3, cipher
suite, key store (client certificate) and trust store are all configurable (the store type is picked from the file
extension). Cipher suites are Java (JSSE) names, for example `TLS_AES_256_GCM_SHA384` (1.3) or
`TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` (1.2).

Passwords (connection, key store, trust store) are encrypted with AES-256-GCM before being written to
`profiles.properties`. The key is random, generated on first run, and kept in `~/.messagepigeon/secret.key`, readable
only by the current OS user. This keeps passwords out of plain-text files, but anyone who can read your whole profile
directory as you can also decrypt them. Leave a password blank in the profile to be asked for it at connect time
instead.

## Third-party software and licensing

The runnable jar bundles these open-source libraries, each under its own licence. They are listed in
[src/main/resources/legal/THIRD-PARTY-NOTICES.txt](src/main/resources/legal/THIRD-PARTY-NOTICES.txt) and shown in the
**Help > About > Third-party licences...** window:

| Component | Licence |
|---|---|
| Bouncy Castle (`bcprov`, `bcpkix`, `bcutil`) | Bouncy Castle Licence (MIT-style) |
| `javax.jms-api` | CDDL 1.1 + GPLv2 with Classpath Exception |
| `org.json` | Public Domain |

**IBM's MQ client library is not part of MessagePigeon and is not in the jar.** It is proprietary software under the
IBM International Program License Agreement, and you supply and load your own copy at run time (see above), so nothing
of IBM's is redistributed with this project.

This project's own licence has not been chosen yet; add a `LICENSE` file before publishing.

## Notes

- A tab reads up to **Max messages** (default 1000); the status bar shows the true queue depth.
- Import from File, Add From File and Add From Folder commit in batches of 500 and roll back the current batch on error.

## Project layout

| Path | Contents |
|---|---|
| `src/main/java/com/messagepigeon` | the application |
| `src/main/resources` | help page and icons |
| `launcher/` | source and icon for the Windows launcher |
| `tools/GenerateIcons.java` | draws the application icons (`java tools/GenerateIcons.java`) |
| `docker/` | local IBM MQ test queue manager |
| `build-exe.ps1` | builds the jar and the exe into `dist/` |
