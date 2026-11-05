import xml.etree.ElementTree as ET

ar_tree = ET.parse('app/src/main/res/values-ar/strings.xml')
en_tree = ET.parse('app/src/main/res/values/strings.xml')

ar_root = ar_tree.getroot()
en_root = en_tree.getroot()

en_keys = {elem.attrib['name'] for elem in en_root if 'name' in elem.attrib}
ar_elements = {elem.attrib['name']: elem.text for elem in ar_root if 'name' in elem.attrib}

missing_in_en = 0
for k, v in ar_elements.items():
    if k not in en_keys:
        elem = ET.SubElement(en_root, 'string')
        elem.attrib['name'] = k
        elem.text = v or k
        missing_in_en += 1

en_tree.write('app/src/main/res/values/strings.xml', encoding='utf-8', xml_declaration=True)
print(f"Added {missing_in_en} missing strings to default strings.xml")
