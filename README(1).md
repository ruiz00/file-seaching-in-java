# UniversalFileSearch

A multithreaded command-line tool that recursively searches every file in a directory tree for a keyword — built in pure Java using the Fork/Join framework for parallel traversal.

## Features

- 🔍 Recursive search through nested folders
- ⚡ Parallel traversal using `ForkJoinPool` / `RecursiveTask` — scales with available CPU cores
- 📄 Case-insensitive keyword matching
- 🧮 Regex search mode (`-r`)
- 🗂️ Extension filtering (`--ext`)
- 🪜 Recursion depth limiting (`--max-depth`)
- 💾 Write results to a file (`--output`)
- 🛡️ Skips unreadable or binary files gracefully instead of crashing
- 🪶 Zero external dependencies — standard library only

## Requirements

- JDK **17 or higher** (compiled and tested on JDK 21)

## Installation

Clone the repository:

```bash
git clone https://github.com/ruiz00/universal-file-search.git
cd universal-file-search
```

Compile:

```bash
javac UniversalFileSearch.java
```

## Usage

```bash
java UniversalFileSearch <folder_path> <keyword> [options]
```

### Options

| Flag | Description |
|---|---|
| `-r` | Treat `<keyword>` as a regular expression instead of plain text |
| `--ext .java,.py` | Only search files with these extensions (comma-separated) |
| `--max-depth N` | Limit recursion to N levels of subfolders below the root |
| `--output results.txt` | Write matches to a file instead of printing them to the console |

### Examples

**Plain keyword search:**
```bash
java UniversalFileSearch ./my-project "TODO"
```

**Regex search** (e.g. find all email addresses):
```bash
java UniversalFileSearch ./my-project "[\w.]+@[\w.]+" -r
```

**Only search Java and Python files:**
```bash
java UniversalFileSearch ./my-project "TODO" --ext .java,.py
```

**Limit recursion to the top-level folder only (no subfolders):**
```bash
java UniversalFileSearch ./my-project "TODO" --max-depth 0
```

**Combine everything and save results to a file:**
```bash
java UniversalFileSearch ./my-project "TODO" --ext .java --max-depth 2 --output results.txt
```

### Searching a single file

```bash
java UniversalFileSearch ./notes.txt "meeting"
```

## How it works

The tool uses Java's **Fork/Join framework** to parallelize the search:

1. For a directory, it spawns one `SearchTask` per entry (sub-folder or file) and forks them in parallel via `invokeAll`.
2. For a file, it checks the extension filter (if any), then reads line by line, matching either a plain substring or a compiled regex pattern.
3. Results are aggregated back up the task tree and summed into a total match count.
4. Matches are printed to the console, or written to the output file if `--output` is set (both are thread-safe via synchronized writes).

`--max-depth N` means: the root folder is depth 0, and the tool will not descend into subfolders beyond depth N. Files directly inside an allowed folder are always searched.

## Limitations

- Does not follow symbolic links that create circular references (treated as regular files to avoid infinite recursion)
- Binary files are skipped silently (detected via decoding errors) rather than searched byte-by-byte
- Regex matching uses Java's `Pattern`/`Matcher` syntax (not POSIX or PCRE)

## Roadmap / ideas for contributors

- [ ] Multi-keyword search (AND/OR logic)
- [ ] Colorized console output for matches
- [ ] Progress indicator for very large trees
- [ ] `.gitignore`-style exclude patterns

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you'd like to change.

## License

[MIT](https://choosealicense.com/licenses/mit/) — free to use, modify, and distribute.

## Author

Built by [Bryan](https://github.com/ruiz00).
