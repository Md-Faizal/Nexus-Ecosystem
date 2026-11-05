import re

with open('scratch_logo.txt', 'r', encoding='utf-8') as f:
    content = f.read()

jsx_content = content.replace('class=', 'className=').replace('fill-rule=', 'fillRule=').replace('clip-rule=', 'clipRule=').replace('stroke-width=', 'strokeWidth=')

# Find the first </a> tag
a_end_idx = jsx_content.find('</a')
a_tag_end = jsx_content.find('>', a_end_idx)
a_tag = jsx_content[:a_tag_end+1]

# The span tag should start right after a_tag
span_start = jsx_content.find('<span', a_tag_end)
span_end = jsx_content.find('</span>', span_start)
span_tag = jsx_content[span_start:span_end+7]

a_tag = a_tag.replace('href="/"', 'href="/" onClick={(e) => { e.preventDefault(); onClick?.(); }}')

react_code = 'import React from "react";\n\nexport default function BrandLogo({ onClick }) {\n  return (\n    <>\n' + a_tag + '\n' + span_tag + '\n    </>\n  );\n}\n'

with open('frontend/src/components/BrandLogo.jsx', 'w', encoding='utf-8') as f:
    f.write(react_code)
print('Successfully generated BrandLogo.jsx')
