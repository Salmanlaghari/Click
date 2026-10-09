package com.click.browser.ui.screens

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Google AdMob ad units for Click Browser (Prince's AdMob account).
 *
 * - [BANNER_AD_UNIT]: 320x50 banner shown in the news feed.
 * - [NATIVE_AD_UNIT]: native ad styled like a news card, always labeled "Ad".
 *
 * AdMob policy notes honored here:
 * - Native ads are clearly labeled with an "Ad" badge.
 * - Ads never sit flush against tappable content — outer padding is applied
 *   by the callers (see NewsSection).
 */
object AdMobUnits {
    const val BANNER_AD_UNIT = "ca-app-pub-8178045957849630/7144411627"
    const val NATIVE_AD_UNIT = "ca-app-pub-8178045957849630/3385130010"
}

/**
 * Standard 320x50 banner ad. The AdView manages its own lifecycle;
 * it is destroyed when the composable leaves the composition.
 */
@Composable
fun AdMobBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = AdMobUnits.BANNER_AD_UNIT
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    DisposableEffect(Unit) {
        adView.loadAd(AdRequest.Builder().build())
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Native ad rendered as a news-style card so it blends with the feed,
 * with a mandatory "Ad" badge per AdMob policy.
 *
 * The card only appears once an ad has loaded — no empty placeholder
 * is left in the feed when loading fails.
 *
 * @param cardBg card background matching the current theme
 * @param cardBorder card border color matching the current theme
 * @param onSurface text color matching the current theme
 */
@Composable
fun AdMobNativeAd(
    cardBg: Color,
    cardBorder: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        val loader = AdLoader.Builder(context, AdMobUnits.NATIVE_AD_UNIT)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd?.destroy()
                    nativeAd = null
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        AndroidView(
            factory = { ctx ->
                // Build the NativeAdView programmatically (no XML layouts in this module).
                val density = ctx.resources.displayMetrics.density
                val adView = NativeAdView(ctx)

                val root = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    val pad = (12 * density).toInt()
                    setPadding(pad, pad, pad, pad)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // "Ad" badge — required by AdMob policy for native ads.
                val badge = TextView(ctx).apply {
                    text = "Ad"
                    textSize = 10f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(Color(0xFFB8860B).toArgb())
                    val hPad = (8 * density).toInt()
                    val vPad = (3 * density).toInt()
                    setPadding(hPad, vPad, hPad, vPad)
                }
                // Wrap badge so it doesn't stretch full width.
                val badgeRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(badge)
                }
                root.addView(badgeRow)

                val mediaView = MediaView(ctx).apply {
                    val h = (140 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, h
                    ).apply { topMargin = (8 * density).toInt() }
                }
                root.addView(mediaView)

                val headline = TextView(ctx).apply {
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(onSurface.toArgb())
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (8 * density).toInt() }
                }
                root.addView(headline)

                val body = TextView(ctx).apply {
                    textSize = 11f
                    setTextColor(onSurface.toArgb())
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (4 * density).toInt() }
                }
                root.addView(body)

                val advertiser = TextView(ctx).apply {
                    textSize = 10.5f
                    val c = onSurface.toArgb()
                    setTextColor(
                        android.graphics.Color.argb(
                            140,
                            android.graphics.Color.red(c),
                            android.graphics.Color.green(c),
                            android.graphics.Color.blue(c)
                        )
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (4 * density).toInt() }
                }
                root.addView(advertiser)

                val iconView = ImageView(ctx).apply {
                    val s = (40 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(s, s).apply {
                        topMargin = (8 * density).toInt()
                    }
                }
                root.addView(iconView)

                // Register asset views with the NativeAdView.
                adView.headlineView = headline
                adView.bodyView = body
                adView.advertiserView = advertiser
                adView.mediaView = mediaView
                adView.iconView = iconView

                adView.addView(root)
                adView.tag = NativeAdBinding(headline, body, advertiser, mediaView, iconView)
                adView
            },
            update = { adView ->
                val binding = adView.tag as? NativeAdBinding ?: return@AndroidView
                binding.headline.text = ad.headline
                binding.body.text = ad.body
                if (ad.advertiser != null) {
                    binding.advertiser.text = ad.advertiser
                    binding.advertiser.visibility = View.VISIBLE
                } else {
                    binding.advertiser.visibility = View.GONE
                }
                // MediaView is required for video ads; guard when no media.
                if (ad.mediaContent != null) {
                    binding.media.mediaContent = ad.mediaContent
                    binding.media.visibility = View.VISIBLE
                } else {
                    binding.media.visibility = View.GONE
                }
                if (ad.icon != null) {
                    binding.icon.setImageDrawable(ad.icon!!.drawable)
                    binding.icon.visibility = View.VISIBLE
                } else {
                    binding.icon.visibility = View.GONE
                }
                adView.setNativeAd(ad)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Holds the programmatically built asset views for native-ad binding. */
private data class NativeAdBinding(
    val headline: TextView,
    val body: TextView,
    val advertiser: TextView,
    val media: MediaView,
    val icon: ImageView
)
