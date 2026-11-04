package org.schemetools;

/** Product composition, independent of network permission checks. */
public enum Edition {
    PLUS("scheme-size-plus", "st.edition.plus", false),
    GOD("scheme-size-god", "st.edition.god", true);
    public final String modId, titleKey;
    public final boolean events;
    Edition(String modId, String titleKey, boolean events) { this.modId = modId; this.titleKey = titleKey; this.events = events; }
}
