package com.orion.store.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.orion.store.R
import com.orion.store.data.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One thing Orion does. [go] jumps straight to it, when it has a home of its own. */
private class Feature(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int, val go: (MainViewModel.() -> Unit)? = null)

private class Chapter(val id: String, @StringRes val title: Int, @StringRes val blurb: Int, val icon: ImageVector, val color: Color, val shape: RoundedPolygon, val features: List<Feature>)

/** Every feature with its chapter, in reading order: Orion AI answers "how do I..." from this and can open the right screen. */
val GUIDE: List<Pair<Int, Feature>> by lazy { CHAPTERS.flatMap { c -> c.features.map { c.title to it } } }

private fun settings(page: String): MainViewModel.() -> Unit = { openSettings(page) }

/** Every feature in the app, chapter by chapter. Anything new that users can see or do gets a line here. */
private val CHAPTERS = listOf(
    Chapter("store", R.string.guide_the_store, R.string.guide_three_catalogs_two_layouts_and_a, Sym.Apps, Color(0xFF6366F1), MaterialShapes.Cookie9Sided, listOf(
        Feature(Sym.Android, R.string.guide_apps_pc_and_tv, R.string.guide_separate_catalogs_for_android_apps_pc, { navTo(Tab.ANDROID) }),
        Feature(Sym.ViewQuilt, R.string.guide_modern_and_classic_layouts, R.string.guide_modern_is_a_storefront_with_a, settings("interface")),
        Feature(Sym.Star, R.string.guide_featured_hero, R.string.guide_hand_picked_apps_glide_past_at),
        Feature(Sym.AutoAwesome, R.string.guide_recommended_for_you, R.string.guide_a_swipeable_carousel_of_daily_picks),
        Feature(Sym.NewReleases, R.string.guide_new_and_updated, R.string.guide_the_latest_additions_and_refreshed_apps),
        Feature(Sym.Category, R.string.guide_category_shelves, R.string.guide_expressive_category_blobs_and_lanes_like),
        Feature(Sym.StarRate, R.string.guide_most_popular_and_hidden_gems, R.string.guide_two_charts_after_the_bundles_rank),
        Feature(Sym.Layers, R.string.guide_app_bundles, R.string.guide_curated_folders_of_apps_that_belong),
        Feature(Sym.LibraryAdd, R.string.guide_your_own_bundles, R.string.guide_build_a_bundle_in_three_steps, { open(Overlay.CUSTOM_BUNDLE) }),
        Feature(Sym.AutoAwesome, R.string.guide_orion_ai, R.string.guide_tap_the_star_and_describe_what, { open(Overlay.AI) }),
        Feature(Sym.Search, R.string.guide_search, R.string.guide_the_search_pill_at_the_top),
        Feature(Sym.SwapVert, R.string.guide_sort_and_filter, R.string.guide_sort_by_newest_oldest_name_or),
        Feature(Sym.Favorite, R.string.guide_favorites, R.string.guide_long_press_any_card_swipe_a),
        Feature(Sym.Description, R.string.guide_about_this_app_previews, R.string.guide_classic_rows_show_the_first_lines, settings("interface")),
        Feature(Sym.Sell, R.string.guide_category_icons, R.string.guide_every_category_wears_its_own_icon),
        Feature(Sym.SystemUpdate, R.string.guide_update_deck, R.string.guide_when_updates_are_waiting_a_slim),
        Feature(Sym.Campaign, R.string.guide_announcements, R.string.guide_news_from_the_orion_team_drops_from),
        Feature(Sym.AutoFixHigh, R.string.guide_pull_to_refresh_characters, R.string.guide_pull_down_to_reload_the_catalog, settings("installer")),
        Feature(Sym.KeyboardArrowUp, R.string.guide_back_to_top, R.string.guide_a_floating_arrow_appears_once_you),
        Feature(Sym.AddCircleOutline, R.string.guide_submit_an_app, R.string.guide_suggest_an_app_from_github_gitlab_or, { open(Overlay.SUBMISSION) }),
    )),
    Chapter("page", R.string.guide_app_pages, R.string.guide_everything_about_one_app_and_every, Sym.Info, Color(0xFF0EA5E9), MaterialShapes.Sunny, listOf(
        Feature(Sym.Download, R.string.guide_one_tap_install, R.string.guide_download_install_update_or_open_an),
        Feature(Sym.Image, R.string.guide_screenshots, R.string.guide_scroll_through_screenshots_at_their_real),
        Feature(Sym.History, R.string.guide_versions_and_channels, R.string.guide_pick_stable_beta_alpha_or_nightly_per),
        Feature(Sym.Memory, R.string.guide_right_build_for_your_phone, R.string.guide_orion_picks_the_apk_that_matches),
        Feature(Sym.Star, R.string.guide_github_stars, R.string.guide_apps_hosted_on_github_show_their),
        Feature(Sym.AccountTree, R.string.guide_forks, R.string.guide_when_an_app_s_repo_is),
        Feature(Sym.Science, R.string.guide_inspect_package, R.string.guide_tap_inspect_in_the_stats_row, settings("installer")),
        Feature(Sym.Info, R.string.detail_app_info, R.string.guide_developer_size_package_name_and_source),
        Feature(Sym.NewReleases, R.string.about_what_s_new, R.string.guide_the_release_notes_of_the_version),
        Feature(Sym.Extension, R.string.guide_patches, R.string.guide_modded_builds_list_every_patch_applied),
        Feature(Sym.Delete, R.string.guide_uninstall, R.string.guide_remove_an_installed_app_from_its),
        Feature(Sym.Policy, R.string.guide_virustotal_scan, R.string.guide_check_a_download_an_installed_apk),
        Feature(Sym.Code, R.string.guide_source_and_safety, R.string.guide_jump_to_the_source_code_the),
        Feature(Sym.NotificationsOff, R.string.guide_ignore_updates, R.string.guide_hide_an_update_for_a_week),
        Feature(Sym.VisibilityOff, R.string.new_don_t_manage_an_app, R.string.new_keep_an_app_you_update_elsewhere_out),
        Feature(Sym.Person, R.string.new_developer_page, R.string.new_tap_the_developer_s_name_on_an),
        Feature(Sym.SaveAlt, R.string.guide_export_and_redownload, R.string.guide_save_the_apk_to_your_downloads),
        Feature(Sym.Share, R.string.guide_share_links_and_badges, R.string.guide_share_a_short_web_link_rookieenough),
        Feature(Sym.EditNote, R.string.guide_request_a_change, R.string.guide_spotted_a_wrong_icon_dead_link),
        Feature(Sym.Report, R.string.detail_report_an_issue, R.string.guide_a_download_broken_report_it_from),
        Feature(Sym.Extension, R.string.guide_microg_helper, R.string.guide_apps_that_need_microg_say_so),
        Feature(Sym.Link, R.string.guide_deep_links, R.string.guide_orionstore_links_and_android_s_app),
    )),
    Chapter("library", R.string.guide_my_apps_and_downloads, R.string.guide_your_library_your_queue_and_updates, Sym.GridView, Color(0xFF22C55E), MaterialShapes.Clover4Leaf, listOf(
        Feature(Sym.Inventory2, R.string.guide_library, R.string.guide_every_app_you_have_downloaded_or, { navTo(Tab.MYAPPS) }),
        Feature(Sym.Delete, R.string.guide_uninstall_several_apps, R.string.guide_long_press_an_installed_app_in),
        Feature(Sym.Downloading, R.string.guide_download_queue, R.string.guide_active_downloads_ready_installs_and_pending_updates, settings("queue")),
        Feature(Sym.Notifications, R.string.guide_background_downloads, R.string.guide_downloads_keep_going_when_you_leave),
        Feature(Sym.Autorenew, R.string.guide_auto_update, R.string.guide_orion_checks_for_updates_every_few, settings("network")),
        Feature(Sym.SystemUpdate, R.string.guide_install_automatically, R.string.guide_let_orion_install_the_updates_it_downloads, settings("network")),
        Feature(Sym.Verified, R.string.guide_stable_releases_only, R.string.guide_skip_beta_alpha_and_nightly_builds, settings("network")),
        Feature(Sym.Wifi, R.string.guide_wi_fi_only, R.string.guide_keep_downloads_off_mobile_data, settings("network")),
        Feature(Sym.CleaningServices, R.string.guide_janitor, R.string.guide_leftover_apks_are_cleaned_a_week, settings("storage")),
    )),
    Chapter("orbit", R.string.guide_orbit, R.string.guide_any_app_straight_from_its_source, Sym.Orbit, Color(0xFF14B8A6), MaterialShapes.Cookie9Sided, listOf(
        Feature(Sym.AddLink, R.string.guide_add_any_app_by_link, R.string.guide_paste_a_github_gitlab_codeberg_sourcehut, { navTo(Tab.ORBIT) }),
        Feature(Sym.WarningAmber, R.string.guide_not_for_orbit, R.string.guide_revanced_apps_and_metrolist_are_not, { navTo(Tab.ORBIT) }),
        Feature(Sym.VisibilityOff, R.string.guide_hide_orbit, R.string.guide_the_first_time_you_open_orbit),
        Feature(Sym.SystemUpdate, R.string.guide_one_tap_install_and_update, R.string.guide_orbit_apps_install_update_and_auto, { navTo(Tab.ORBIT) }),
        Feature(Sym.History, R.string.guide_any_version, R.string.guide_every_recent_release_of_an_orbit),
        Feature(Sym.Tune, R.string.guide_fine_tune, R.string.guide_pick_which_apk_counts_with_a),
        Feature(Sym.Key, R.string.guide_source_tokens, R.string.guide_tokens_on_the_orbit_tab_let, { navTo(Tab.ORBIT) }),
        Feature(Sym.Sell, R.string.guide_tags, R.string.guide_give_an_orbit_app_tags_in, { navTo(Tab.ORBIT) }),
        Feature(Sym.TravelExplore, R.string.guide_discover, R.string.guide_the_obtainium_apps_directory_lives_inside, { navTo(Tab.ORBIT) }),
        Feature(Sym.FileDownload, R.string.guide_obtainium_import_and_export, R.string.guide_bring_your_obtainium_export_file_json),
        Feature(Sym.Publish, R.string.guide_share_with_the_orion_library, R.string.guide_long_press_an_orbit_app_and),
        Feature(Sym.NotificationsOff, R.string.guide_pause_updates, R.string.guide_hold_an_orbit_app_on_the),
    )),
    Chapter("install", R.string.guide_installing_your_way, R.string.guide_from_one_tap_installs_to_fully, Sym.InstallMobile, Color(0xFFF97316), MaterialShapes.Gem, listOf(
        Feature(Sym.Android, R.string.guide_choose_your_installer, R.string.guide_use_the_system_installer_pick_one, settings("installer")),
        Feature(Sym.Speed, R.string.guide_skip_the_24_hour_wait, R.string.guide_built_in_wireless_debugging_installs_apps_instantly, settings("installer")),
        Feature(Sym.InstallMobile, R.string.guide_install_any_apk, R.string.guide_orion_is_an_installer_too_open, settings("installer")),
        Feature(Sym.Science, R.string.guide_inside_this_apk, R.string.guide_the_apk_installer_sheet_reads_the, settings("installer")),
        Feature(Sym.Tune, R.string.guide_options_on_the_apk_sheet, R.string.guide_beyond_install_has_its_own_switch, settings("installer")),
        Feature(Sym.Shield, R.string.guide_sideloading_protection, R.string.guide_when_android_switches_on_its_developer, settings("installer")),
        Feature(Sym.GppGood, R.string.guide_protection_shield, R.string.guide_a_shield_in_the_search_bar, settings("installer")),
        Feature(Sym.Bolt, R.string.guide_silent_installs_with_shizuku, R.string.guide_with_shizuku_running_installs_and_updates, settings("installer")),
        Feature(Sym.AdminPanelSettings, R.string.guide_dhizuku, R.string.guide_silent_installs_updates_and_uninstalls_through, settings("installer")),
        Feature(Sym.Tune, R.string.guide_beyond_install, R.string.guide_before_each_root_or_shizuku_install, settings("installer")),
        Feature(Sym.Terminal, R.string.guide_root_mode, R.string.guide_silent_installs_updates_and_every_guardian, settings("installer")),
        Feature(Sym.Sync, R.string.guide_adopt_installed_apps, R.string.guide_with_root_orion_takes_over_updates, settings("installer")),
        Feature(Sym.Lock, R.string.guide_update_lock, R.string.guide_on_android_14_and_newer_with, settings("installer")),
        Feature(Sym.DeleteSweep, R.string.guide_clear_every_app_s_cache, R.string.guide_with_root_free_space_across_all, settings("installer")),
        Feature(Sym.Refresh, R.string.guide_force_rescan, R.string.guide_reload_the_list_of_installed_apps, settings("installer")),
    )),
    Chapter("xtra", R.string.guide_guardian_and_sentinel, R.string.guide_security_and_control_tools_built_right, Sym.Shield, Color(0xFFEF4444), MaterialShapes.Cookie6Sided, listOf(
        Feature(Sym.Shield, R.string.guide_guardian_permissions, R.string.guide_spot_apps_holding_risky_permissions_like, settings("installer")),
        Feature(Sym.Unarchive, R.string.guide_guardian_extract_apk, R.string.guide_pull_the_apk_out_of_any, settings("installer")),
        Feature(Sym.DeleteForever, R.string.guide_guardian_debloat, R.string.guide_remove_preinstalled_system_apps_reversibly_guided, settings("installer")),
        Feature(Sym.MonitorHeart, R.string.guide_sentinel_rapid_and_deep_scans, R.string.guide_check_installed_apps_against_a_threat, settings("installer")),
        Feature(Sym.Lan, R.string.guide_sentinel_network_sentry, R.string.guide_a_live_audit_of_your_connection, settings("installer")),
        Feature(Sym.DeveloperMode, R.string.guide_adb_watchdog, R.string.guide_see_at_a_glance_whether_the),
    )),
    Chapter("look", R.string.guide_look_and_feel, R.string.guide_make_orion_look_and_move_the, Sym.Palette, Color(0xFFA855F7), MaterialShapes.Flower, listOf(
        Feature(Sym.LightMode, R.string.guide_light_dusk_dark_and_oled, R.string.guide_four_themes_the_button_in_the, settings("visuals")),
        Feature(Sym.Palette, R.string.guide_color_palettes, R.string.guide_fourteen_palettes_from_orion_to_cocoa, settings("visuals")),
        Feature(Sym.FormatColorFill, R.string.guide_make_your_own_palette, R.string.guide_tap_make_in_the_palette_row, settings("visuals")),
        Feature(Sym.AutoAwesome, R.string.guide_material_you, R.string.guide_take_your_colors_from_your_wallpaper, settings("visuals")),
        Feature(Sym.FontDownload, R.string.guide_fonts, R.string.guide_eleven_built_in_fonts_or_import, settings("visuals")),
        Feature(Sym.Compress, R.string.guide_compact_mode, R.string.guide_smaller_tiles_slimmer_rows_and_an, settings("visuals")),
        Feature(Sym.BlurOn, R.string.guide_glass_dock, R.string.guide_a_floating_slightly_translucent_navigation_dock, settings("visuals")),
        Feature(Sym.ViewQuilt, R.string.guide_big_screen_navigation, R.string.guide_on_tablets_foldables_tvs_and_wide, settings("visuals")),
        Feature(Sym.ViewAgenda, R.string.guide_dock_styles, R.string.guide_pill_classic_icons_labeled_expressive_and, settings("visuals")),
        Feature(Sym.Palette, R.string.guide_app_icons, R.string.guide_six_launcher_icons_beyond_the_classic, settings("visuals")),
        Feature(Sym.OpenInFull, R.string.guide_dock_size, R.string.guide_scale_the_dock_between_compact_and_large, settings("visuals")),
        Feature(Sym.TouchApp, R.string.guide_haptic_feedback, R.string.guide_tactile_ticks_on_taps_toggles_and, settings("visuals")),
        Feature(Sym.Speed, R.string.guide_smooth_motion, R.string.guide_prefer_the_highest_refresh_rate_your, settings("visuals")),
        Feature(Sym.Bolt, R.string.new_performance_mode, R.string.new_lite_keeps_older_phones_smooth_by_stopping, settings("visuals")),
        Feature(Sym.MotionPhotosOff, R.string.guide_disable_animations, R.string.guide_every_transition_snaps_instantly, settings("visuals")),
        Feature(Sym.ViewAgenda, R.string.guide_hide_tabs, R.string.guide_hide_the_apps_pc_tv_or, settings("interface")),
        Feature(Sym.OpenInFull, R.string.guide_full_screen, R.string.guide_hide_the_status_bar_while_orion, settings("visuals")),
        Feature(Sym.Tv, R.string.guide_android_tv_beta, R.string.guide_orion_runs_on_android_tv_it_opens),
        Feature(Sym.DesktopWindows, R.string.guide_tablets_and_landscape, R.string.guide_orion_rotates_freely_on_tablets_and),
    )),
    Chapter("settings", R.string.guide_settings_and_backup, R.string.guide_settings_arranged_your_way_and_progress, Sym.Settings, Color(0xFFEC4899), MaterialShapes.Pentagon, listOf(
        Feature(Sym.ToggleOn, R.string.guide_quick_toggles, R.string.guide_pin_the_switches_you_flip_most, { openSettings() }),
        Feature(Sym.DragHandle, R.string.guide_arrange_the_settings_hub, R.string.guide_long_press_a_settings_card_and, { openSettings() }),
        Feature(Sym.Backup, R.string.guide_identity_backup, R.string.guide_save_your_profile_xp_badges_favorites, settings("identity")),
        Feature(Sym.Cloud, R.string.guide_automatic_cloud_backup, R.string.guide_connect_google_drive_onedrive_proton_drive, settings("identity")),
        Feature(Sym.CloudOff, R.string.guide_serverless_by_design, R.string.guide_no_orion_account_and_no_orion),
        Feature(Sym.VerifiedUser, R.string.guide_integrity_checks, R.string.guide_backups_with_impossible_progress_are_refused),
    )),
    Chapter("profile", R.string.guide_profile_xp_and_badges, R.string.guide_every_app_ad_and_run_moves, Sym.EmojiEvents, Color(0xFFEAB308), MaterialShapes.Cookie12Sided, listOf(
        Feature(Sym.Badge, R.string.guide_pilot_card, R.string.guide_a_holographic_id_that_tilts_with, { openProfileStats() }),
        Feature(Sym.Draw, R.string.guide_signature, R.string.guide_sign_the_back_of_your_pilot),
        Feature(Sym.Person, R.string.guide_alias_and_avatar, R.string.guide_pick_an_alien_alias_and_an, { openProfileStats() }),
        Feature(Sym.Bolt, R.string.guide_xp, R.string.guide_earn_10_xp_for_each_app_in),
        Feature(Sym.TrendingUp, R.string.guide_levels_and_ranks, R.string.guide_each_level_takes_a_little_more),
        Feature(Sym.Diamond, R.string.guide_metal_card_skins, R.string.guide_bronze_silver_and_gold_pilots_get),
        Feature(Sym.Category, R.string.guide_collection_badges, R.string.guide_unlock_both_locked_icons_for_icon),
        Feature(Sym.MilitaryTech, R.string.community_badge_archive, R.string.guide_every_badge_you_can_earn_how, { profileClick("badges", 0) }),
        Feature(Sym.QueryStats, R.string.guide_stats, R.string.guide_favorites_downloads_xp_and_rank_counting, { openProfileStats() }),
    )),
    Chapter("play", R.string.guide_play_and_compete, R.string.guide_missions_a_mini_game_and_a, Sym.SportsEsports, Color(0xFF14B8A6), MaterialShapes.SoftBurst, listOf(
        Feature(Sym.Swords, R.string.about_fuel_the_code, R.string.guide_an_optional_quest_across_two_chapters, { open(Overlay.AD_DONATION) }),
        Feature(Sym.Castle, R.string.guide_bosses_and_badges, R.string.guide_defeat_the_code_golem_the_bug),
        Feature(Sym.Egg, R.string.games_orion_run, R.string.guide_a_dino_runner_that_keeps_you),
        Feature(Sym.EmojiEvents, R.string.about_hall_of_fame_2, R.string.guide_tap_the_developer_coin_on_the_dev, { navTo(Tab.ABOUT) }),
        Feature(Sym.Gavel, R.string.guide_fair_play, R.string.guide_daily_limits_on_missions_and_the),
    )),
    Chapter("dev", R.string.guide_the_dev_tab_and_beyond, R.string.guide_help_support_and_a_few_things, Sym.Code, Color(0xFF64748B), MaterialShapes.Pill, listOf(
        Feature(Sym.MenuBook, R.string.guide_know_orion, R.string.guide_this_page_every_feature_in_one),
        Feature(Sym.HelpOutline, R.string.guide_faqs, R.string.guide_answers_on_safety_revanced_builds_failed, { open(Overlay.FAQ) }),
        Feature(Sym.NewReleases, R.string.about_what_s_new, R.string.guide_release_notes_straight_from_orion_s, { open(Overlay.RELEASE_NOTES) }),
        Feature(Sym.Forum, R.string.guide_connect, R.string.guide_github_x_discord_and_the_orion, { navTo(Tab.ABOUT) }),
        Feature(Sym.Coffee, R.string.about_support_orion, R.string.guide_buy_the_developer_a_coffee_through, { navTo(Tab.ABOUT) }),
        Feature(Sym.Public, R.string.guide_mirrors_and_offline, R.string.guide_the_catalog_loads_from_github_jsdelivr),
        Feature(Sym.Construction, R.string.guide_maintenance_and_store_updates, R.string.guide_orion_tells_you_when_the_store),
        Feature(Sym.Terminal, R.string.guide_developer_options, R.string.guide_tap_the_orion_logo_seven_times),
        Feature(Sym.QuestionMark, R.string.guide_secrets, R.string.guide_a_few_easter_eggs_and_secret),
    )),
)

/** Orion hides the status bar, so the plain insets read zero and the floating chips slid under the camera cutout. Its slot (and any cutout) is kept clear instead. */
private val GuideTop @Composable get() = WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))

/** Know Orion: the whole app as an expressive, searchable field guide. */
@Composable
fun KnowOrionScreen(vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val cs = MaterialTheme.colorScheme; val list = rememberLazyListState(); val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val context = LocalContext.current
    val shown = remember(q) {
        if (q.isEmpty()) CHAPTERS else CHAPTERS.map { c -> Chapter(c.id, c.title, c.blurb, c.icon, c.color, c.shape, c.features.filter { q in context.getString(it.title).lowercase() || q in context.getString(it.body).lowercase() }) }
            .filter { it.features.isNotEmpty() }
    }
    val total = remember { CHAPTERS.sumOf { it.features.size } }
    fun go(f: Feature) { vm.haptic(H.SELECTION); onClose(); scope.launch { delay(120); f.go?.invoke(vm) } }
    // Hero, search and chips come first; each chapter is a header plus its rows
    val starts = remember(shown) { shown.runningFold(3) { at, c -> at + c.features.size + 1 }.dropLast(1) }
    val current by remember(starts) { derivedStateOf { if (list.firstVisibleItemIndex < 3) -1 else starts.indexOfLast { it <= list.firstVisibleItemIndex + 1 } } }
    val chips = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Rows land just below the floating chip bar instead of under it
    val pin = GuideTop.getTop(density) + with(density) { 84.dp.roundToPx() }
    fun jump(ci: Int) {
        // Far chapters snap straight there: an animated fling across dozens of rows composes every one of them on the way
        val at = starts[ci]
        scope.launch { if (kotlin.math.abs(at - list.firstVisibleItemIndex) <= 8) list.animateScrollToItem(at, -pin) else list.scrollToItem(at, -pin) }
    }
    val floating by remember { derivedStateOf { list.firstVisibleItemIndex >= 3 } }
    LaunchedEffect(current) { if (current >= 0) { if (floating) chips.animateScrollToItem(current) else chips.requestScrollToItem(current) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = readableGutter(maxWidth)
        LazyColumn(Modifier.fillMaxSize().background(cs.surface), list, contentPadding = PaddingValues(start = gutter, end = gutter, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        item("hero") { Box(Modifier.bleed(gutter)) { GuideHero(total, gutter) } }
        item("search") { GuideSearch(query) { query = it } }
        item("chips") { ChapterChips(shown, -1, rememberLazyListState(), ::jump) }
        if (shown.isEmpty()) item("empty") { EmptyState(Sym.SearchOff, stringResource(R.string.guide_nothing_matches, query), stringResource(R.string.guide_try_a_shorter_word_like_backup)) }
        shown.forEach { c ->
            item("h-${c.id}", contentType = "header") { ChapterHeader(c, CHAPTERS.indexOfFirst { it.id == c.id }) }
            itemsIndexed(c.features, key = { _, f -> "${c.id}-${f.title}" }, contentType = { _, _ -> "feature" }) { i, f -> FeatureRow(c, f, i, c.features.size) { go(f) } }
        }
        item("end") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).background(cs.primaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
                    Icon(painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(40.dp), tint = cs.onPrimaryContainer)
                }
                Text(stringResource(R.string.guide_that_s_all_of_orion), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLargeEmphasized)
                Text(stringResource(R.string.guide_features_in_chapters_and_more_on, total, CHAPTERS.size), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                Surface(Modifier.padding(top = 14.dp), shape = Pill, color = cs.surfaceContainerHigh) {
                    Text("v$STORE_VERSION", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    // Past the top, chapters float in a rounded bar under the status bar, marking where you are
    androidx.compose.animation.AnimatedVisibility(floating, Modifier.align(Alignment.TopCenter).windowInsetsPadding(GuideTop).padding(horizontal = 12.dp, vertical = 8.dp),
        enter = androidx.compose.animation.slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut()) {
        Surface(Modifier.widthIn(max = 840.dp), shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerHigh, shadowElevation = 6.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ scope.launch { list.toTop() } }, Modifier.padding(start = 4.dp)) { Icon(Sym.KeyboardArrowUp, stringResource(R.string.guide_back_to_top)) }
                ChapterChips(shown, current, chips, ::jump, start = 0.dp)
            }
        }
    }
    }
}

@Composable
private fun ChapterChips(shown: List<Chapter>, current: Int, state: androidx.compose.foundation.lazy.LazyListState, onJump: (Int) -> Unit, start: Dp = 16.dp) =
    LazyRow(Modifier.fillMaxWidth(), state, contentPadding = PaddingValues(start = start, end = 16.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(shown, key = { _, c -> c.id }) { ci, c -> Choice(ci == current, stringResource(c.title), c.icon) { onJump(ci) } }
    }

private val HERO_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst)

@Composable
private fun GuideHero(total: Int, gutter: Dp) {
    val cs = MaterialTheme.colorScheme; val still = LocalS.current.calm
    var shape by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) { if (!still) while (true) { delay(2600); shape++ } }
    Box(Modifier.fillMaxWidth().clipToBounds().background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface)))) {
        Turning(MaterialShapes.Cookie12Sided, cs.primary.copy(.08f), 280.dp, Modifier.align(Alignment.TopEnd).offset(90.dp, (-40).dp), 50_000)
        Column(Modifier.windowInsetsPadding(GuideTop).padding(horizontal = 20.dp + gutter).padding(top = 8.dp, bottom = 12.dp)) {
            MorphBlob(HERO_SHAPES, shape, 104.dp, cs.primary, Modifier.padding(top = 12.dp)) {
                Icon(painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(72.dp), tint = cs.onPrimary)
            }
            Text(stringResource(R.string.guide_field_guide), Modifier.padding(top = 20.dp), style = MaterialTheme.typography.labelLargeEmphasized, color = cs.primary)
            Text(stringResource(R.string.guide_know_orion), style = MaterialTheme.typography.displayMediumEmphasized)
            Text(stringResource(R.string.guide_every_one_of_orion_s_features, total),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideSearch(query: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(56.dp).clip(Pill).background(cs.surfaceContainerHigh).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Sym.Search, null, tint = cs.onSurfaceVariant)
        BasicTextField(query, onChange, Modifier.weight(1f).padding(horizontal = 12.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
            decorationBox = { field -> Box { if (query.isEmpty()) Text(stringResource(R.string.guide_search_features), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant); field() } })
        if (query.isNotEmpty()) IconButton({ onChange("") }) { Icon(Sym.Close, stringResource(R.string.guide_clear_search)) }
    }
}

@Composable
private fun ChapterHeader(c: Chapter, index: Int) {
    val (bg, fg) = tonal(c.color)
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp).fillMaxWidth().background(bg, RoundedCornerShape(32.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Drawn shapes, not clipped layers or endless spins: cheap to bring on screen when jumping between chapters
        Box(Modifier.size(64.dp).background(fg.copy(.16f), c.shape.toShape()), contentAlignment = Alignment.Center) {
            Icon(c.icon, null, Modifier.size(30.dp), tint = fg)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.guide_chapter, index + 1), style = MaterialTheme.typography.labelMedium, color = fg.copy(.75f))
            Text(stringResource(c.title), style = MaterialTheme.typography.headlineSmallEmphasized, color = fg)
            Text(stringResource(c.blurb), style = MaterialTheme.typography.bodyMedium, color = fg.copy(.85f))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.background(fg, Pill).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("${c.features.size}", style = MaterialTheme.typography.labelLargeEmphasized, color = bg) }
    }
}

/** One feature as a plain segmented row: a much lighter build than the interactive list item, so long jumps stay smooth. */
@Composable
private fun FeatureRow(c: Chapter, f: Feature, i: Int, n: Int, onGo: () -> Unit) {
    val (bg, fg) = tonal(c.color); val cs = MaterialTheme.colorScheme
    val shape = colShape(i, n)
    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 2.dp).fillMaxWidth().clip(shape).background(cs.surfaceContainer)
        .then(if (f.go != null) Modifier.clickable(onClick = onGo) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(bg, MaterialShapes.Cookie6Sided.toShape()), contentAlignment = Alignment.Center) { Icon(f.icon, null, Modifier.size(22.dp), tint = fg) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(f.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(f.body), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (f.go != null) { Spacer(Modifier.width(12.dp)); Icon(Sym.ArrowForward, stringResource(R.string.guide_open, stringResource(f.title)), tint = fg) }
    }
}

/** A MaterialShape turning slowly in place; holds still when animations are off. */
@Composable
private fun Turning(shape: RoundedPolygon, color: Color, size: Dp, modifier: Modifier = Modifier, periodMs: Int = 20_000) {
    val still = LocalS.current.calm
    val r by rememberInfiniteTransition(label = "turn").animateFloat(0f, if (still) 0f else 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "r")
    Box(modifier.size(size).graphicsLayer { rotationZ = r }.background(color, shape.toShape()))
}
