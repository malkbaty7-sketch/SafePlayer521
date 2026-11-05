import xml.etree.ElementTree as ET

en_strings = {
    'tab_video': 'Video',
    'tab_music': 'Music',
    'tab_my_files': 'My Files',
    'tab_playlist': 'Playlist',
    'folder_count': '%d Folders',
    'recently_added': 'Recently Added',
    'recently_added_count': '%d newly added videos',
    'recently_added_audio_count': '%d newly added audio files'
}

ar_strings = {
    'tab_video': 'فيديو',
    'tab_music': 'موسيقى',
    'tab_my_files': 'ملفاتي',
    'tab_playlist': 'قائمة التشغيل',
    'folder_count': '%d مجلدات',
    'recently_added': 'تمت إضافته مؤخراً',
    'recently_added_count': '%d تمت إضافة مقاطع الفيديو',
    'recently_added_audio_count': '%d تمت إضافة ملفات صوتية'
}

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

add_strings('app/src/main/res/values/strings.xml', en_strings)
add_strings('app/src/main/res/values-ar/strings.xml', ar_strings)

