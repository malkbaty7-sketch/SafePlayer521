import xml.etree.ElementTree as ET
import sys

def add_strings(filepath, new_strings):
    try:
        tree = ET.parse(filepath)
        root = tree.getroot()
        existing = {elem.attrib['name']: elem for elem in root if 'name' in elem.attrib}
        
        for name, text in new_strings.items():
            if name in existing:
                existing[name].text = text
            else:
                elem = ET.SubElement(root, 'string')
                elem.attrib['name'] = name
                elem.text = text
                
        tree.write(filepath, encoding='utf-8', xml_declaration=True)
        print(f"Updated {filepath}")
    except Exception as e:
        print(f"Failed to update {filepath}: {e}")

en_strings = {
    'title_theme_color': 'Theme Color',
    'title_app_color_theme': 'App Color Theme',
    'theme_bronze': 'Bronze (Default)',
    'theme_ocean': 'Ocean (Blue)',
    'theme_forest': 'Forest (Green)',
    'theme_slate': 'Slate (Purple/Gray)',
    'action_select_theme': 'Select Theme Color',
    'desc_toggle_day_night': 'Toggle Day/Night Mode'
}

ar_strings = {
    'title_theme_color': 'لون المظهر (الثيم)',
    'title_app_color_theme': 'لون التطبيق',
    'theme_bronze': 'برونزي (الافتراضي)',
    'theme_ocean': 'محيطي (أزرق)',
    'theme_forest': 'غابي (أخضر)',
    'theme_slate': 'أردوازي (أرجواني/رمادي)',
    'action_select_theme': 'اختر لون المظهر',
    'desc_toggle_day_night': 'تبديل الوضع الليلي/النهاري'
}

add_strings('app/src/main/res/values/strings.xml', en_strings)
add_strings('app/src/main/res/values-ar/strings.xml', ar_strings)

