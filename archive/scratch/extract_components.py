import os
import re
from bs4 import BeautifulSoup, Comment

with open('pentori_skeleton.html', 'r', encoding='utf-8') as f:
    html = f.read()

soup = BeautifulSoup(html, 'html.parser')

def process_node(node):
    if not hasattr(node, 'children'):
        return
    for child in list(node.children):
        if isinstance(child, Comment):
            child.extract()
        else:
            process_node(child)

process_node(soup)

def html_to_jsx(html_str):
    jsx = html_str.replace('class=', 'className=')
    jsx = jsx.replace('fill-rule=', 'fillRule=').replace('clip-rule=', 'clipRule=').replace('stroke-width=', 'strokeWidth=')
    jsx = jsx.replace('stroke-linecap=', 'strokeLinecap=').replace('stroke-linejoin=', 'strokeLinejoin=')
    jsx = re.sub(r'<!--.*?-->', '', jsx, flags=re.DOTALL)
    # self-closing tags
    jsx = re.sub(r'<img([^>]*)>', r'<img\1 />', jsx)
    jsx = re.sub(r'<video([^>]*)>', r'<video\1 />', jsx)
    jsx = re.sub(r'<video([^>]*)\s*/>\s*</video>', r'<video\1 />', jsx)
    jsx = re.sub(r'<track([^>]*)>', r'<track\1 />', jsx)
    
    jsx = jsx.replace('<number-flow-react>', '<div className="number-flow-react">').replace('</number-flow-react>', '</div>')
    return jsx

def create_component(name, element):
    if not element:
        print(f"Element not found for {name}")
        return
    jsx = html_to_jsx(str(element))
    content = f'''import React from "react";

export default function {name}() {{
  return (
{jsx}
  );
}}
'''
    with open(f'frontend/src/components/{name}.jsx', 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"Created {name}.jsx")

marquee1 = soup.find('div', class_=re.compile(r'mt-30 md:mt-60'))
create_component('Marquee1', marquee1)

marquee2 = soup.find('div', class_=re.compile(r'mt-32 md:mt-92'))
create_component('Marquee2', marquee2)
