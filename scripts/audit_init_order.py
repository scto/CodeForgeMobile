#!/usr/bin/env python3
import os
import re

def audit_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    lines = content.split('\n')
    
    # Check if there is an init block
    init_line_idx = -1
    for idx, line in enumerate(lines):
        if re.search(r'\binit\s*\{', line):
            init_line_idx = idx
            break
            
    if init_line_idx == -1:
        return None

    # Check for properties declared after init block
    # Find matching closing brace for init block
    depth = 0
    init_end_idx = -1
    for idx in range(init_line_idx, len(lines)):
        line = lines[idx]
        depth += line.count('{') - line.count('}')
        if depth == 0:
            init_end_idx = idx
            break

    if init_end_idx == -1:
        init_end_idx = init_line_idx

    # Look for class-level properties after init_end_idx
    # (properties with indentation matching class level, e.g., 4 spaces)
    post_init_props = []
    
    # Detect class level indentation
    class_indent = 4
    for idx in range(init_end_idx + 1, len(lines)):
        line = lines[idx]
        # Skip empty lines or comment lines
        stripped = line.strip()
        if not stripped or stripped.startswith('//') or stripped.startswith('/*') or stripped.startswith('*'):
            continue
            
        # Match val/var at top level of class
        m = re.match(r'^(\s{4}|\t)(private\s+|protected\s+|internal\s+|public\s+)?(val|var)\s+([a-zA-Z0-9_]+)', line)
        if m:
            prop_name = m.group(4)
            post_init_props.append((idx + 1, prop_name, line.strip()))

    return {
        'file': filepath,
        'init_line': init_line_idx + 1,
        'post_init_props': post_init_props
    }

def main():
    root_dirs = ['app', 'core', 'feature', 'libs']
    findings = []
    
    for rdir in root_dirs:
        for root, dirs, files in os.walk(rdir):
            for file in files:
                if file.endswith('.kt'):
                    path = os.path.join(root, file)
                    res = audit_file(path)
                    if res and res['post_init_props']:
                        findings.append(res)

    print(f"Total files with properties declared after init: {len(findings)}")
    for f in findings:
        print(f"\nFile: {f['file']} (init at line {f['init_line']})")
        for line_no, name, decl in f['post_init_props']:
            print(f"  Line {line_no}: {name} -> {decl}")

if __name__ == '__main__':
    main()
