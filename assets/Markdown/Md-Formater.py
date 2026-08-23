# ﻿python markdown_formatter.py mein_dokument.md

# **Erweiterter Aufruf (erstellt eine neue Datei und setzt einen Titel):**
# ```bash
# python markdown_formatter.py mein_dokument.md -o fertig.md -t "API Spezifikation"

# Lass mich wissen, falls ich das Skript noch um weitere Funktionen (wie z.B. das Umwandeln von Markdown-Tabellen) ergänzen soll – oder falls du es doch lieber in **Kotlin** haben möchtest!


import re
import argparse
from pathlib import Path
from datetime import datetime
from typing import List, Tuple, Optional

class MarkdownFormatter:
    """
    Eine Klasse, um Markdown-Dokumente modern zu strukturieren,
    Metadaten hinzuzufügen, ein Inhaltsverzeichnis (TOC) zu generieren
    und Tabellen sauber auszurichten.
    """
    
    def __init__(self, file_path: Path):
        self.file_path = file_path
        self.content = self._read_file()
        self.frontmatter = ""
        self.body = ""
        self.headings: List[Tuple[int, str]] = []

    def _read_file(self) -> str:
        """Liest den Inhalt der Datei."""
        if not self.file_path.exists():
            raise FileNotFoundError(f"Die Datei {self.file_path} wurde nicht gefunden.")
        with open(self.file_path, 'r', encoding='utf-8') as f:
            return f.read()

    def _split_frontmatter(self):
        """Trennt vorhandenes YAML-Frontmatter vom Rest des Dokuments."""
        pattern = re.compile(r'^(---\s*\n.*?\n?---)\s*\n', re.DOTALL)
        match = pattern.match(self.content)
        
        if match:
            self.frontmatter = match.group(1)
            self.body = self.content[match.end():]
        else:
            self.frontmatter = ""
            self.body = self.content

    def _generate_frontmatter(self, title: str = "Unbenanntes Dokument"):
        """Generiert ein modernes YAML-Frontmatter, falls keines existiert."""
        if not self.frontmatter:
            heute = datetime.now().strftime("%Y-%m-%d")
            self.frontmatter = f"---\ntitle: {title}\ndate: {heute}\nauthor: Autor Name\ntags: [markdown, dokumentation]\n---\n"

    def _extract_headings(self):
        """Extrahiert alle Überschriften für das Inhaltsverzeichnis."""
        self.headings = []
        pattern = re.compile(r'^(#{1,6})\s+(.+)$', re.MULTILINE)
        for match in pattern.finditer(self.body):
            level = len(match.group(1))
            text = match.group(2).strip()
            if "Inhaltsverzeichnis" not in text:
                self.headings.append((level, text))

    def _create_toc(self) -> str:
        """Erstellt das Inhaltsverzeichnis (Table of Contents)."""
        if not self.headings:
            return ""

        toc_lines = ["## Inhaltsverzeichnis\n"]
        for level, text in self.headings:
            anchor = re.sub(r'[^a-z0-9\s-]', '', text.lower())
            anchor = re.sub(r'\s+', '-', anchor.strip())
            indent = "  " * (level - 1)
            toc_lines.append(f"{indent}- [{text}](#{anchor})")
            
        return "\n".join(toc_lines) + "\n\n"

    def _format_spacing(self):
        """Sorgt für einheitliche Abstände bei Überschriften."""
        self.body = re.sub(r'\n{3,}', '\n\n', self.body)
        # Leerzeile vor Überschriften (wenn nicht am Anfang)
        self.body = re.sub(r'([^\n])\n(#{1,6}\s+.+)', r'\1\n\n\2', self.body)
        # Leerzeile nach Überschriften
        self.body = re.sub(r'(#{1,6}\s+.+)\n([^\n])', r'\1\n\n\2', self.body)

    def _format_tables(self):
        """
        Findet Markdown-Tabellen und richtet die Spalten sauber aus.
        """
        # RegEx, um mögliche Tabellenblöcke zu finden (Zeilen, die mit | beginnen oder Enden und | enthalten)
        table_pattern = re.compile(r'((?:\|?.*\|.*\|?\n)+)', re.MULTILINE)
        
        def align_table(match):
            block = match.group(1).strip()
            lines = block.split('\n')
            
            # Prüfen, ob es wirklich eine Tabelle ist (mindestens 2 Zeilen, und die zweite Zeile sieht aus wie ein Trenner)
            if len(lines) < 2 or not re.match(r'^\|?[\s\-\:]+\|[\s\-\:\|]+\|?$', lines[1]):
                return match.group(0) # Keine gültige Tabelle, unverändert zurückgeben

            parsed_rows = []
            for line in lines:
                # Entferne äußere Pipes, falls vorhanden
                line = line.strip()
                if line.startswith('|'): line = line[1:]
                if line.endswith('|'): line = line[:-1]
                
                # Spalten aufteilen und Leerzeichen trimmen
                cols = [col.strip() for col in line.split('|')]
                parsed_rows.append(cols)

            if not parsed_rows:
                return match.group(0)

            # Maximale Breite pro Spalte ermitteln
            num_cols = max(len(row) for row in parsed_rows)
            col_widths = [0] * num_cols
            
            for row in parsed_rows:
                for i, col in enumerate(row):
                    if i < num_cols:
                        # Trennzeile ignorieren für die Breitenberechnung
                        if not set(col).issubset({'-', ':', ' '}):
                            col_widths[i] = max(col_widths[i], len(col))

            # Mindestbreite für Tabellenspalten (z.B. wegen :---:)
            col_widths = [max(width, 3) for width in col_widths]

            # Tabelle neu zusammenbauen
            formatted_lines = []
            for row_idx, row in enumerate(parsed_rows):
                formatted_cols = []
                for col_idx in range(num_cols):
                    # Spaltenwert holen, falls vorhanden, sonst leer
                    val = row[col_idx] if col_idx < len(row) else ""
                    width = col_widths[col_idx]
                    
                    if row_idx == 1: # Trennzeile (---)
                        # Ausrichtung beibehalten, falls vorhanden
                        left = ":" if val.startswith(":") else "-"
                        right = ":" if val.endswith(":") else "-"
                        fill = "-" * (width - 2) if (left == ":" and right == ":") else ("-" * (width - 1) if left == ":" or right == ":" else "-" * width)
                        formatted_cols.append(f" {left}{fill}{right} ")
                    else: # Normale Zeile
                        # Wert rechts mit Leerzeichen auffüllen, um Breite zu erreichen
                        formatted_cols.append(f" {val.ljust(width)} ")
                        
                formatted_lines.append("|" + "|".join(formatted_cols) + "|")

            return "\n" + "\n".join(formatted_lines) + "\n\n"

        self.body = table_pattern.sub(align_table, self.body)

    def format(self, doc_title: str = "Modernes Dokument"):
        """Führt alle Formatierungsschritte durch."""
        self._split_frontmatter()
        self._generate_frontmatter(doc_title)
        
        # Inhaltsverzeichnis entfernen, falls schon eins da ist, um Duplikate zu vermeiden
        self.body = re.sub(r'## Inhaltsverzeichnis\n+(-\s+\[.+\]\(.+\)\n*)+\n*', '', self.body)
        
        self._format_spacing()
        self._format_tables()  # NEU: Tabellen formatieren
        self._extract_headings()
        
        toc = self._create_toc()
        
        # Alles zusammensetzen
        self.content = f"{self.frontmatter}\n{toc}{self.body.strip()}\n"

    def save(self, output_path: Optional[Path] = None):
        """Speichert das formatierte Dokument."""
        out_path = output_path or self.file_path
        with open(out_path, 'w', encoding='utf-8') as f:
            f.write(self.content)
        print(f"Erfolgreich formatiert und gespeichert unter: {out_path}")

def main():
    parser = argparse.ArgumentParser(description="Strukturiert Markdown-Dokumente modern inkl. Tabellen-Formatierung.")
    parser.add_argument("input_file", type=str, help="Pfad zur Eingabe-Markdown-Datei")
    parser.add_argument("-o", "--output", type=str, help="Pfad zur Ausgabe-Datei (überschreibt Input, falls leer)")
    parser.add_argument("-t", "--title", type=str, default="Dokumentation", help="Titel für die Metadaten")

    args = parser.parse_args()

    input_path = Path(args.input_file)
    output_path = Path(args.output) if args.output else input_path

    try:
        formatter = MarkdownFormatter(input_path)
        formatter.format(doc_title=args.title)
        formatter.save(output_path)
    except Exception as e:
        print(f"Fehler bei der Verarbeitung: {e}")

if __name__ == "__main__":
    main()