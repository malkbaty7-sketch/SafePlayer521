import xml.etree.ElementTree as ET

en_tree = ET.parse('app/src/main/res/values/strings.xml')
en_root = en_tree.getroot()
en_dict = {elem.attrib['name']: elem.text for elem in en_root if 'name' in elem.attrib}

files = ['values-es', 'values-fr', 'values-de', 'values-zh']
for f in files:
    path = f'app/src/main/res/{f}/strings.xml'
    try:
        tree = ET.parse(path)
        root = tree.getroot()
        existing = {elem.attrib['name']: elem for elem in root if 'name' in elem.attrib}
        
        for name, text in en_dict.items():
            if name not in existing:
                elem = ET.SubElement(root, 'string')
                elem.attrib['name'] = name
                elem.text = text
                
        tree.write(path, encoding='utf-8', xml_declaration=True)
        print(f"Updated {path}")
    except Exception as e:
        print(f"Failed to update {path}: {e}")

