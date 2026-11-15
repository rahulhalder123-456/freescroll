//! Known social apps (Android package names) and social web domains.
//! Anything not listed is still caught if Android marks it CATEGORY_SOCIAL (checked on the Java side).

pub const SOCIAL_PACKAGES: &[&str] = &[
    "com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
    "com.instagram.android", "com.instagram.barcelona", "com.instagram.lite",
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.tiktok.lite",
    "com.google.android.youtube", "com.google.android.apps.youtube.mango", "app.revanced.android.youtube",
    "com.twitter.android", "com.twitter.android.lite",
    "com.snapchat.android", "com.reddit.frontpage", "com.pinterest", "com.tumblr",
    "com.linkedin.android", "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
    "org.thunderdog.challegram", "com.discord", "com.bumble.app", "com.tinder", "com.hinge.app",
    "com.sharechat.app", "in.mohalla.sharechat", "com.mxtech.videoplayer.ad", "com.roposo.android",
    "in.mohalla.video", "com.kwai.video", "com.kwai.bulldog", "com.vkontakte.android",
    "com.bereal.ft", "social.media.threads", "com.mastodon.android", "org.joinmastodon.android",
    "com.bsky.app", "xyz.blueskyweb.app", "com.vsco.cam", "com.twitch.android.app",
    "com.likee.video", "video.like", "com.imo.android.imoim", "com.viber.voip",
    "jp.naver.line.android", "com.tencent.mm", "com.sina.weibo", "com.quora.android",
    "com.clubhouse.app", "com.yubo.app", "com.lemon8.android", "com.truthsocial.android.app",
];

/// (domain suffix) for social sites opened in a browser.
pub const SOCIAL_DOMAINS: &[&str] = &[
    "facebook.com", "fb.com", "fb.watch", "instagram.com", "tiktok.com", "youtube.com", "youtu.be",
    "x.com", "twitter.com", "reddit.com", "snapchat.com", "pinterest.com", "tumblr.com",
    "linkedin.com", "threads.net", "discord.com", "twitch.tv", "telegram.org", "web.telegram.org",
    "web.whatsapp.com", "bsky.app", "mastodon.social", "vk.com", "weibo.com", "quora.com",
    "sharechat.com", "moj.app", "likee.video", "kwai.com", "bereal.com", "tinder.com", "bumble.com",
];

/// Browsers whose URL bar we try to read, with the view-id of their address field.
pub const BROWSER_URL_IDS: &[(&str, &str)] = &[
    ("com.android.chrome", "com.android.chrome:id/url_bar"),
    ("com.chrome.beta", "com.chrome.beta:id/url_bar"),
    ("org.mozilla.firefox", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
    ("org.mozilla.fenix", "org.mozilla.fenix:id/mozac_browser_toolbar_url_view"),
    ("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text"),
    ("com.microsoft.emmx", "com.microsoft.emmx:id/url_bar"),
    ("com.brave.browser", "com.brave.browser:id/url_bar"),
    ("com.opera.browser", "com.opera.browser:id/url_field"),
    ("com.opera.mini.native", "com.opera.mini.native:id/url_field"),
    ("com.duckduckgo.mobile.android", "com.duckduckgo.mobile.android:id/omnibarTextInput"),
    ("com.vivaldi.browser", "com.vivaldi.browser:id/url_bar"),
    ("com.kiwibrowser.browser", "com.kiwibrowser.browser:id/url_bar"),
    ("com.android.browser", "com.android.browser:id/url"),
    ("com.mi.globalbrowser", "com.mi.globalbrowser:id/url"),
    ("com.UCMobile.intl", "com.UCMobile.intl:id/url_bar"),
];

pub fn is_social_package(pkg: &str) -> bool { SOCIAL_PACKAGES.contains(&pkg) }

pub fn url_id_for_browser(pkg: &str) -> Option<&'static str> {
    BROWSER_URL_IDS.iter().find(|(p, _)| *p == pkg).map(|(_, id)| *id)
}

/// Pulls a host out of whatever the URL bar shows ("m.facebook.com/reel/1", "https://x.com/…").
/// Returns "web:<matching social domain>" or None.
pub fn site_for_url(text: &str) -> Option<String> {
    let t = text.trim().to_lowercase();
    let t = t.split("://").last().unwrap_or(&t);
    let host = t.split(|c| c == '/' || c == '?' || c == '#' || c == ' ').next().unwrap_or("");
    let host = host.split('@').last().unwrap_or(host).split(':').next().unwrap_or(host);
    SOCIAL_DOMAINS.iter()
        .find(|d| host == **d || host.ends_with(&format!(".{}", d)))
        .map(|d| format!("web:{}", d))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn urls() {
        assert_eq!(site_for_url("m.facebook.com/reel/123").as_deref(), Some("web:facebook.com"));
        assert_eq!(site_for_url("https://www.instagram.com/").as_deref(), Some("web:instagram.com"));
        assert_eq!(site_for_url("x.com/home").as_deref(), Some("web:x.com"));
        assert_eq!(site_for_url("https://notfacebook.com/").as_deref(), None);
        assert_eq!(site_for_url("example.com/?u=facebook.com"), None);
        assert_eq!(site_for_url("music.youtube.com/watch").as_deref(), Some("web:youtube.com"));
        assert_eq!(site_for_url("google.com"), None);
    }
    #[test]
    fn packages() {
        assert!(is_social_package("com.instagram.android"));
        assert!(!is_social_package("com.android.settings"));
        assert_eq!(url_id_for_browser("com.android.chrome"), Some("com.android.chrome:id/url_bar"));
    }
}
