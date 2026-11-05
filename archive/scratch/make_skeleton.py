from bs4 import BeautifulSoup
import json

with open('pentori_clean.html', 'r', encoding='utf-8') as f:
    html = f.read()

soup = BeautifulSoup(html, 'html.parser')

def strip_content(element):
    if not hasattr(element, 'children'):
        return
    for child in list(element.children):
        if child.name is None: # text node
            if child.string and child.string.strip():
                # keep some text for context
                pass
        else:
            # Keep class, id, src, href
            allowed_attrs = ['class', 'id', 'data-section']
            new_attrs = {}
            for k, v in child.attrs.items():
                if k in allowed_attrs:
                    new_attrs[k] = v
            child.attrs = new_attrs
            strip_content(child)

main_tag = soup.find('main')
if main_tag:
    strip_content(main_tag)
    with open('pentori_skeleton.html', 'w', encoding='utf-8') as f:
        f.write(main_tag.prettify())
    print("Skeleton created successfully.")
else:
    print("Main tag not found.")
