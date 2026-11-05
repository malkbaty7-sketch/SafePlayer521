import xml.etree.ElementTree as ET

en_strings = {
    'player_speed': 'Speed',
    'player_night_mode': 'Night Mode',
    'player_ab_repeat': 'AB Repeat',
    'player_pip': 'Pop-up Window',
    'player_equalizer': 'Equalizer',
    'player_mirror': 'Mirror',
    'player_orientation': 'Orientation',
    'player_mute': 'Mute',
    'player_unmute': 'Unmute',
    'player_bg_play': 'Background Play',
    'player_screen_locked': 'Controls Locked',
    'player_unlock_prompt': 'Tap to unlock screen',
    'player_sleep_timer': 'Sleep Timer',
    'player_sleep_timer_off': 'Off',
    'player_sleep_timer_end_video': 'End of Video',
    'player_video_info': 'Video Information',
    'player_safe_mode': 'Safe / Family Mode',
    'player_safe_mode_active': 'Safe Mode Active: Settings and Deletions Locked',
    'player_aspect_fit': 'Fit',
    'player_aspect_fill': 'Fill',
    'player_aspect_zoom': 'Zoom',
    'player_aspect_crop': 'Crop',
    'player_brightness': 'Brightness',
    'player_volume': 'Volume',
    'player_subtitles': 'Subtitles',
    'player_audio_tracks': 'Audio Tracks',
    'player_playlist': 'Playlist',
    'player_no_subtitles': 'No subtitles found',
    'player_rewind_10': 'Rewind 10s',
    'player_forward_10': 'Forward 10s',
    'action_close': 'Close'
}

ar_strings = {
    'player_speed': 'سرعة',
    'player_night_mode': 'الوضع الليلي',
    'player_ab_repeat': 'كرر AB',
    'player_pip': 'تشغيل نافذة',
    'player_equalizer': 'هدف التعادل',
    'player_mirror': 'مرآة',
    'player_orientation': 'أفقي',
    'player_mute': 'كتم',
    'player_unmute': 'إلغاء الكتم',
    'player_bg_play': 'تشغيل في الخلفية',
    'player_screen_locked': 'الشاشة مقفلة',
    'player_unlock_prompt': 'انقر لفتح قفل الشاشة',
    'player_sleep_timer': 'مؤقت النوم',
    'player_sleep_timer_off': 'إيقاف',
    'player_sleep_timer_end_video': 'نهاية الفيديو',
    'player_video_info': 'معلومات الفيديو',
    'player_safe_mode': 'الوضع العائلي الآمن',
    'player_safe_mode_active': 'الوضع الآمن مفعل: منع الحذف وتعديل الإعدادات',
    'player_aspect_fit': 'ملاءمة',
    'player_aspect_fill': 'ملء الشاشة',
    'player_aspect_zoom': 'تكبير',
    'player_aspect_crop': 'اقتصاص',
    'player_brightness': 'السطوع',
    'player_volume': 'مستوى الصوت',
    'player_subtitles': 'الترجمة',
    'player_audio_tracks': 'المسارات الصوتية',
    'player_playlist': 'قائمة التشغيل',
    'player_no_subtitles': 'لا توجد ترجمات',
    'player_rewind_10': 'رجوع 10 ثواني',
    'player_forward_10': 'تقديم 10 ثواني',
    'action_close': 'إغلاق'
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

