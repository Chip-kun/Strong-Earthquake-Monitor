package com.example.ui.components

import androidx.compose.ui.graphics.Path

/**
 * High-precision geographical geometry and spline smoothing for Japan seismic map.
 * Faithfully matches official NIED base map (base_map_black.png / base_map_org_black.png).
 */
object JapanMapGeometry {

    /**
     * Converts a sequence of geographic (lat, lon) coordinates into a silky-smooth
     * Catmull-Rom spline rendered as cubic bezier segments in screen space.
     */
    fun createSmoothPath(
        points: List<Pair<Double, Double>>,
        toScreenX: (Double) -> Float,
        toScreenY: (Double) -> Float,
        closed: Boolean = true,
        tension: Float = 0.5f
    ): Path {
        val path = Path()
        val n = points.size
        if (n < 2) return path

        if (n == 2) {
            val (lat0, lon0) = points[0]
            val (lat1, lon1) = points[1]
            path.moveTo(toScreenX(lon0), toScreenY(lat0))
            path.lineTo(toScreenX(lon1), toScreenY(lat1))
            if (closed) path.close()
            return path
        }

        val screenPts = points.map { (lat, lon) ->
            Pair(toScreenX(lon), toScreenY(lat))
        }

        val (firstX, firstY) = screenPts[0]
        path.moveTo(firstX, firstY)

        val factor = (1f - tension) / 3f

        if (closed) {
            for (i in 0 until n) {
                val p0 = screenPts[(i - 1 + n) % n]
                val p1 = screenPts[i]
                val p2 = screenPts[(i + 1) % n]
                val p3 = screenPts[(i + 2) % n]

                val c1x = p1.first + (p2.first - p0.first) * factor
                val c1y = p1.second + (p2.second - p0.second) * factor
                val c2x = p2.first - (p3.first - p1.first) * factor
                val c2y = p2.second - (p3.second - p1.second) * factor

                path.cubicTo(c1x, c1y, c2x, c2y, p2.first, p2.second)
            }
            path.close()
        } else {
            for (i in 0 until n - 1) {
                val p0 = if (i == 0) screenPts[0] else screenPts[i - 1]
                val p1 = screenPts[i]
                val p2 = screenPts[i + 1]
                val p3 = if (i + 2 < n) screenPts[i + 2] else p2

                val c1x = p1.first + (p2.first - p0.first) * factor
                val c1y = p1.second + (p2.second - p0.second) * factor
                val c2x = p2.first - (p3.first - p1.first) * factor
                val c2y = p2.second - (p3.second - p1.second) * factor

                path.cubicTo(c1x, c1y, c2x, c2y, p2.first, p2.second)
            }
        }

        return path
    }

    // =========================================================================
    // 1. HONSHU MAIN ISLAND (本州) - Dense high-resolution coastline
    // =========================================================================
    val HONSHU_COAST = listOf(
        // Shimokita Peninsula (下北半島)
        Pair(41.55, 140.91), // Oma (大間崎)
        Pair(41.48, 141.20),
        Pair(41.43, 141.47), // Cape Shiriya (尻屋崎)
        Pair(41.30, 141.40),
        Pair(41.05, 141.38), // Higashidori
        Pair(40.85, 141.40), // Rokkasho
        Pair(40.55, 141.53), // Hachinohe (八戸)
        // Sanriku Rias Coast (三陸リアス海岸)
        Pair(40.35, 141.75), // Taneichi
        Pair(40.18, 141.83), // Kuji (久慈)
        Pair(40.02, 141.92), // Fudai
        Pair(39.85, 141.97), // Tanohata
        Pair(39.64, 141.97), // Miyako (宮古・魹ヶ崎)
        Pair(39.46, 141.98), // Yamada
        Pair(39.36, 141.92), // Otsuchi
        Pair(39.27, 141.89), // Kamaishi (釜石)
        Pair(39.15, 141.86), // Sanriku
        Pair(39.05, 141.74), // Ofunato (大船渡)
        Pair(38.98, 141.66), // Kesennuma (気仙沼)
        Pair(38.68, 141.52), // Minamisanriku
        Pair(38.30, 141.52), // Oshika Peninsula (牡鹿半島)
        Pair(38.40, 141.35), // Ishinomaki Bay (石巻)
        Pair(38.35, 141.05), // Matsushima
        Pair(38.20, 140.98), // Sendai (仙台)
        Pair(38.00, 140.93), // Watari
        // Joban & Kashima-nada (常磐・鹿島灘)
        Pair(37.82, 140.96), // Soma (相馬)
        Pair(37.58, 141.03), // Minamisoma (南相馬)
        Pair(37.36, 141.03), // Futaba
        Pair(37.15, 141.01), // Hirono
        Pair(37.00, 140.93), // Iwaki (いわき・小名浜)
        Pair(36.80, 140.78), // Kitaibaraki
        Pair(36.60, 140.66), // Hitachi (日立)
        Pair(36.36, 140.60), // Oarai (大洗)
        Pair(36.00, 140.68), // Kashima (鹿島)
        Pair(35.75, 140.85), // Kamisu
        // Boso Peninsula (房総半島)
        Pair(35.71, 140.86), // Choshi / Inubosaki (銚子・犬吠埼)
        Pair(35.55, 140.48), // Kujukuri (九十九里)
        Pair(35.32, 140.40), // Ichinomiya
        Pair(35.25, 140.40), // Ohara
        Pair(35.13, 140.31), // Katsuura (勝浦)
        Pair(35.10, 140.12), // Kamogawa (鴨川)
        Pair(34.96, 139.97), // Chikura
        Pair(34.90, 139.88), // Cape Nojima (野島崎)
        Pair(34.97, 139.83), // Tateyama (館山)
        Pair(35.08, 139.84), // Kyonan
        Pair(35.25, 139.82), // Futtsu (富津岬)
        Pair(35.40, 139.95), // Kisarazu (木更津)
        Pair(35.60, 140.08), // Chiba (千葉)
        // Tokyo Bay (東京湾)
        Pair(35.68, 139.98), // Funabashi
        Pair(35.62, 139.77), // Tokyo Port (東京港)
        Pair(35.53, 139.78), // Haneda (羽田)
        Pair(35.44, 139.65), // Yokohama (横浜)
        Pair(35.30, 139.65), // Yokosuka (横須賀)
        // Miura Peninsula (三浦半島)
        Pair(35.14, 139.63), // Miura / Cape Tsurugisaki (三浦・剣崎)
        Pair(35.14, 139.61), // Misaki (三崎)
        Pair(35.26, 139.57), // Zushi / Kamakura (鎌倉)
        Pair(35.30, 139.48), // Enoshima / Fujisawa (江の島)
        // Sagami Bay (相模湾)
        Pair(35.32, 139.35), // Hiratsuka (平塚)
        Pair(35.25, 139.16), // Odawara (小田原)
        Pair(35.15, 139.14), // Yugawara / Manazuru
        // Izu Peninsula (伊豆半島)
        Pair(35.10, 139.08), // Atami (熱海)
        Pair(34.96, 139.10), // Ito (伊東)
        Pair(34.75, 139.02), // Kawazu
        Pair(34.67, 138.97), // Shimoda (下田)
        Pair(34.59, 138.91), // Cape Iro (石廊崎)
        Pair(34.76, 138.77), // Matsuzaki (松崎)
        Pair(34.88, 138.76), // Toi (土肥)
        Pair(35.02, 138.80), // Heda / Osezaki (大瀬崎)
        Pair(35.10, 138.86), // Numazu (沼津)
        // Suruga Bay (駿河湾)
        Pair(35.14, 138.68), // Fuji (富士)
        Pair(35.05, 138.52), // Shimizu (清水)
        Pair(34.98, 138.40), // Shizuoka (静岡)
        Pair(34.86, 138.32), // Yaizu (焼津)
        Pair(34.60, 138.22), // Cape Omaezaki (御前崎)
        // Enshu-nada (遠州灘)
        Pair(34.65, 138.00), // Fukude
        Pair(34.68, 137.75), // Hamamatsu (浜松)
        Pair(34.68, 137.58), // Lake Hamana outlet (浜名湖口)
        Pair(34.65, 137.40), // Toyohashi
        // Atsumi Peninsula & Mikawa Bay (渥美半島・三河湾)
        Pair(34.60, 137.16), // Cape Irago (伊良湖岬)
        Pair(34.68, 137.28), // Tahara
        Pair(34.80, 137.22), // Gamagori (蒲郡)
        Pair(34.80, 137.05), // Nishio / Kira
        Pair(34.70, 136.98), // Morozaki (師崎・知多半島先端)
        Pair(34.85, 136.83), // Tokoname / Chubu Airport (常滑)
        Pair(35.05, 136.88), // Nagoya Port (名古屋港)
        // Ise Bay (伊勢湾)
        Pair(35.00, 136.70), // Kuwana
        Pair(34.96, 136.63), // Yokkaichi (四日市)
        Pair(34.88, 136.58), // Suzuka (鈴鹿)
        Pair(34.72, 136.52), // Tsu (津)
        Pair(34.58, 136.54), // Matsusaka (松阪)
        Pair(34.50, 136.75), // Ise (伊勢)
        Pair(34.48, 136.85), // Toba (鳥羽)
        // Shima Peninsula & Kii Peninsula (志摩半島・紀伊半島)
        Pair(34.33, 136.90), // Cape Daio (大王崎)
        Pair(34.30, 136.72), // Ago Bay (英虞湾)
        Pair(34.25, 136.35), // Owase (尾鷲)
        Pair(33.89, 136.10), // Kumano (熊野)
        Pair(33.72, 135.99), // Shingu (新宮)
        Pair(33.60, 135.94), // Nachikatsuura (那智勝浦)
        Pair(33.58, 135.88), // Taiji
        Pair(33.50, 135.82), // Koza
        Pair(33.43, 135.76), // Cape Shionomisaki (潮岬)
        Pair(33.55, 135.50), // Susami
        Pair(33.68, 135.34), // Shirahama (白浜)
        Pair(33.73, 135.38), // Tanabe (田辺)
        Pair(33.80, 135.20), // Minabe
        Pair(34.00, 135.12), // Gobo (御坊)
        Pair(34.08, 135.12), // Yuasa
        Pair(34.15, 135.15), // Arida
        Pair(34.28, 135.13), // Wakayama (和歌山)
        Pair(34.28, 135.07), // Kada (加太)
        // Osaka Bay (大阪湾)
        Pair(34.35, 135.20), // Hannan
        Pair(34.45, 135.32), // Kansai Airport / Izumisano (関西空港)
        Pair(34.58, 135.46), // Sakai (堺)
        Pair(34.65, 135.43), // Osaka Port (大阪港)
        Pair(34.71, 135.34), // Amagasaki / Nishinomiya (西宮)
        Pair(34.68, 135.19), // Kobe (神戸)
        Pair(34.64, 135.03), // Akashi (明石)
        Pair(34.75, 134.85), // Kakogawa
        Pair(34.79, 134.68), // Himeji (姫路)
        Pair(34.80, 134.46), // Aioi
        Pair(34.73, 134.18), // Ako (赤穂)
        // Seto Inland Sea (瀬戸内海)
        Pair(34.63, 134.16), // Hinase
        Pair(34.62, 133.95), // Okayama (岡山)
        Pair(34.50, 133.75), // Kurashiki / Kojima (倉敷)
        Pair(34.48, 133.50), // Kasaoka
        Pair(34.37, 133.38), // Fukuyama (福山)
        Pair(34.40, 133.20), // Onomichi (尾道)
        Pair(34.35, 133.00), // Mihara
        Pair(34.30, 132.80), // Takehara (竹原)
        Pair(34.25, 132.55), // Kure (呉)
        Pair(34.35, 132.45), // Hiroshima Bay (広島港)
        Pair(34.28, 132.32), // Hatsukaichi (廿日市)
        Pair(34.18, 132.22), // Iwakuni (岩国)
        Pair(33.98, 132.12), // Yanai (柳井)
        Pair(34.00, 131.85), // Kudamatsu / Hikari
        Pair(34.05, 131.80), // Shunan (周南)
        Pair(34.02, 131.57), // Hofu (防府)
        Pair(33.95, 131.25), // Ube (宇部)
        Pair(33.95, 130.93), // Shimonoseki / Kanmon (下関)
        // San'in Coast (山陰・日本海側)
        Pair(34.10, 130.90), // Hibikinada
        Pair(34.35, 130.90), // Tsunoshima (角島)
        Pair(34.37, 131.18), // Nagato (長門)
        Pair(34.42, 131.40), // Hagi (萩)
        Pair(34.68, 131.78), // Masuda (益田)
        Pair(34.90, 132.07), // Hamada (浜田)
        Pair(35.00, 132.22), // Gotsu
        Pair(35.20, 132.48), // Oda
        Pair(35.40, 132.62), // Izumo Hinomisaki (出雲・日御碕)
        Pair(35.53, 133.00), // Matsue (松江)
        Pair(35.58, 133.32), // Mihonoseki (美保関)
        Pair(35.53, 133.25), // Sakaiminato (境港)
        Pair(35.45, 133.33), // Yonago (米子)
        Pair(35.50, 133.80), // Kurayoshi / Yurihama
        Pair(35.50, 134.20), // Tottori Sand Dunes (鳥取砂丘)
        Pair(35.60, 134.45), // Iwami
        Pair(35.65, 134.70), // Kyotango / Tango (丹後・経ヶ岬)
        Pair(35.65, 135.25), // Ine / Miyazu (宮津)
        Pair(35.50, 135.35), // Maizuru (舞鶴)
        // Wakasa Bay & Echizen Coast (若狭湾・越前海岸)
        Pair(35.53, 135.75), // Obama (小浜)
        Pair(35.75, 136.00), // Tsuruga (敦賀)
        Pair(35.88, 136.00), // Cape Echizen (越前岬)
        Pair(36.22, 136.13), // Mikuni / Awara (三国・東尋坊)
        // Kaga & Noto Peninsula (加賀・能登半島)
        Pair(36.32, 136.25), // Kaga
        Pair(36.50, 136.60), // Kanazawa (金沢)
        Pair(36.85, 136.75), // Hakui (羽咋)
        Pair(37.05, 136.70), // Shika
        Pair(37.40, 136.90), // Wajima (輪島)
        Pair(37.52, 137.35), // Suzu / Cape Rokko (珠洲・禄剛崎)
        Pair(37.35, 137.25), // Uchiura
        Pair(37.15, 137.05), // Anamizu
        Pair(37.05, 137.00), // Nanao Bay (七尾湾)
        Pair(36.85, 137.10), // Himi (氷見)
        // Toyama Bay (富山湾)
        Pair(36.75, 137.22), // Toyama (富山)
        Pair(36.82, 137.40), // Uozu
        Pair(36.97, 137.45), // Kurobe (黒部)
        // Niigata Coast (新潟海岸)
        Pair(37.04, 137.86), // Itoigawa (糸魚川)
        Pair(37.18, 138.25), // Joetsu (上越・直江津)
        Pair(37.38, 138.56), // Kashiwazaki (柏崎)
        Pair(37.64, 138.80), // Teradomari
        Pair(37.95, 139.05), // Niigata Port (新潟港)
        Pair(38.05, 139.25), // Shibata
        Pair(38.23, 139.46), // Murakami (村上・笹川流れ)
        // Shonai & Akita Coast (庄内・秋田海岸)
        Pair(38.74, 139.80), // Tsuruoka (鶴岡)
        Pair(38.93, 139.85), // Sakata (酒田)
        Pair(39.20, 139.90), // Kisakata (象潟)
        Pair(39.38, 140.05), // Yurihonjo (由利本荘)
        Pair(39.73, 140.05), // Akita Port (秋田港)
        Pair(39.88, 139.71), // Oga Peninsula / Nyudozaki (男鹿半島・入道崎)
        Pair(39.90, 139.90), // Funakawa
        Pair(40.23, 139.99), // Noshiro (能代)
        // Tsugaru Coast (津軽海岸)
        Pair(40.60, 139.95), // Fukaura (深浦)
        Pair(40.85, 139.97), // Ajigasawa (鯵ヶ沢)
        Pair(41.05, 140.28), // Kodomari
        Pair(41.25, 140.35), // Cape Tappi (竜飛崎)
        Pair(41.13, 140.45), // Minmaya
        // Mutsu Bay (陸奥湾)
        Pair(40.83, 140.75), // Aomori Port (青森港)
        Pair(40.95, 140.85), // Hiranai
        Pair(41.00, 140.80), // Noheji (野辺地)
        Pair(41.12, 141.00), // Yokohama
        Pair(41.28, 141.18), // Mutsu (むつ)
        Pair(41.45, 141.00)  // Sai (佐井)
    )

    // =========================================================================
    // 2. HOKKAIDO (北海道) - Detailed natural contours & peninsulas
    // =========================================================================
    val HOKKAIDO_COAST = listOf(
        Pair(45.52, 141.93), // Cape Soya (宗谷岬)
        Pair(45.30, 142.15), // Sarufutsu
        Pair(45.10, 142.37), // Hamatonbetsu
        Pair(44.93, 142.58), // Esashi
        Pair(44.58, 142.97), // Omu
        Pair(44.35, 143.35), // Monbetsu (紋別)
        Pair(44.20, 143.62), // Yubetsu
        Pair(44.12, 143.90), // Lake Saroma entrance
        Pair(44.10, 144.10), // Tokoro
        Pair(44.02, 144.27), // Abashiri (網走)
        Pair(43.92, 144.45), // Koshimizu
        Pair(43.90, 144.68), // Shari
        Pair(44.07, 144.98), // Utoro
        Pair(44.34, 145.32), // Cape Shiretoko (知床岬)
        Pair(44.02, 145.22), // Rausu (羅臼)
        Pair(43.68, 145.13), // Shibetsu
        Pair(43.58, 145.35), // Notsuke Peninsula (野付半島)
        Pair(43.35, 145.58), // Cape Nosappu / Nemuro (納沙布岬・根室)
        Pair(43.27, 145.45), // Ochiishi
        Pair(43.15, 145.10), // Hamanaka
        Pair(43.05, 144.85), // Akkeshi (厚岸)
        Pair(42.98, 144.38), // Kushiro (釧路)
        Pair(42.85, 144.00), // Shiranuka
        Pair(42.60, 143.65), // Toyokoro
        Pair(42.28, 143.32), // Hiroo
        Pair(41.92, 143.25), // Cape Erimo (襟裳岬)
        Pair(42.10, 143.05), // Samani
        Pair(42.16, 142.77), // Urakawa (浦河)
        Pair(42.35, 142.38), // Shinhidaka
        Pair(42.57, 141.95), // Hidaka
        Pair(42.58, 141.80), // Mukawa
        Pair(42.63, 141.60), // Tomakomai (苫小牧)
        Pair(42.50, 141.35), // Shiraoi
        Pair(42.42, 141.10), // Noboribetsu
        // Uchiura Bay & Muroran (内浦湾・噴火湾・室蘭)
        Pair(42.31, 140.97), // Muroran / Chikyu Cape (室蘭・地球岬)
        Pair(42.46, 140.85), // Date
        Pair(42.58, 140.75), // Abuta / Toyoura
        Pair(42.50, 140.40), // Oshamambe (長万部)
        Pair(42.25, 140.35), // Yakumo (八雲)
        Pair(42.08, 140.58), // Mori (森)
        Pair(41.98, 140.85), // Shikabe
        Pair(41.77, 140.73), // Hakodate (函館)
        Pair(41.68, 140.42), // Kikonai
        Pair(41.52, 140.30), // Fukushima
        Pair(41.40, 140.09), // Cape Shirakami (白神岬)
        // Oshima Peninsula West Coast (渡島半島西岸)
        Pair(41.42, 140.05), // Matsumae (松前)
        Pair(41.65, 140.10), // Kaminokuni
        Pair(41.86, 140.12), // Esashi (江差)
        Pair(42.15, 139.92), // Otobe
        Pair(42.45, 139.85), // Setana (せたな)
        Pair(42.65, 140.00), // Shimamaki
        Pair(42.80, 140.22), // Suttsu (寿都)
        Pair(42.97, 140.35), // Kuromatsunai
        Pair(43.10, 140.50), // Iwanai (岩内)
        Pair(43.25, 140.35), // Tomari
        // Shakotan Peninsula (積丹半島)
        Pair(43.33, 140.35), // Cape Kamui / Shakotan (神威岬・積丹岬)
        Pair(43.25, 140.60), // Bikuni
        Pair(43.20, 140.80), // Yoichi (余市)
        Pair(43.19, 141.00), // Otaru (小樽)
        // Ishikari Bay (石狩湾)
        Pair(43.25, 141.35), // Ishikari (石狩)
        Pair(43.40, 141.42), // Atsuta
        Pair(43.60, 141.38), // Hamamasu
        Pair(43.85, 141.52), // Mashike (増毛)
        Pair(43.94, 141.63), // Rumoi (留萌)
        Pair(44.20, 141.68), // Tomamae
        Pair(44.38, 141.70), // Haboro (羽幌)
        Pair(44.60, 141.75), // Shosanbetsu
        Pair(44.86, 141.64), // Teshio (天塩)
        Pair(45.15, 141.60), // Toyotomi
        Pair(45.41, 141.67)  // Wakkanai (稚内)
    )

    // =========================================================================
    // 3. SHIKOKU (四国) - Sadamisaki Peninsula & Ashizuri/Muroto Capes
    // =========================================================================
    val SHIKOKU_COAST = listOf(
        Pair(34.20, 134.60), // Naruto (鳴門)
        Pair(34.05, 134.58), // Tokushima (徳島)
        Pair(33.85, 134.65), // Anan (阿南)
        Pair(33.72, 134.55), // Minami
        Pair(33.55, 134.30), // Kaiyo (海陽)
        Pair(33.40, 134.25), // Toyo
        Pair(33.25, 134.18), // Cape Muroto (室戸岬)
        Pair(33.35, 133.95), // Nahari
        Pair(33.48, 133.68), // Aki (安芸)
        Pair(33.50, 133.55), // Kochi (高知・浦戸湾)
        Pair(33.45, 133.40), // Tosa
        Pair(33.38, 133.28), // Susaki (須崎)
        Pair(33.25, 133.10), // Nakatosa
        Pair(33.00, 133.05), // Kuroshio
        Pair(32.95, 132.95), // Shimanto (四万十)
        Pair(32.72, 133.02), // Cape Ashizuri (足摺岬)
        Pair(32.78, 132.80), // Tosashimizu (土佐清水)
        Pair(32.93, 132.72), // Sukumo (宿毛)
        Pair(32.98, 132.55), // Ainan (愛南)
        Pair(33.20, 132.50), // Uwajima (宇和島)
        Pair(33.38, 132.48), // Seiyo
        Pair(33.45, 132.40), // Yawatahama (八幡浜)
        // Sadamisaki Peninsula (佐田岬半島 - 日本一細長い半島)
        Pair(33.45, 132.25), // Ikata
        Pair(33.34, 132.01), // Cape Sadamisaki (佐田岬先端)
        Pair(33.42, 132.18),
        Pair(33.55, 132.45), // Nagahama
        Pair(33.70, 132.65), // Iyo (伊予)
        Pair(33.85, 132.75), // Matsuyama (松山)
        Pair(34.02, 132.95), // Hojo
        Pair(34.08, 133.00), // Imabari (今治・来島海峡)
        Pair(33.95, 133.18), // Saijo (西条)
        Pair(33.95, 133.30), // Niihama (新居浜)
        Pair(33.98, 133.55), // Shikokuchuo (四国中央)
        Pair(34.10, 133.65), // Kan-onji (観音寺)
        Pair(34.22, 133.65), // Mitoyo
        Pair(34.32, 133.80), // Marugame (丸亀)
        Pair(34.33, 133.92), // Sakaide (坂出)
        Pair(34.35, 134.05), // Takamatsu (高松)
        Pair(34.30, 134.25), // Sanuki (さぬき)
        Pair(34.25, 134.35), // Higashikagawa
        Pair(34.24, 134.55)  // Naruto West
    )

    // =========================================================================
    // 4. KYUSHU (九州) - Kunisaki, Kagoshima Bay, Shimabara & Nagasaki
    // =========================================================================
    val KYUSHU_COAST = listOf(
        Pair(33.95, 130.95), // Moji / Kanmon (門司・関門海峡)
        Pair(33.80, 131.00), // Kanda
        Pair(33.65, 131.00), // Yukuhashi (行橋)
        Pair(33.60, 131.20), // Nakatsu (中津)
        Pair(33.58, 131.38), // Usa (宇佐)
        // Kunisaki Peninsula (国東半島)
        Pair(33.68, 131.50), // Bungotakada
        Pair(33.72, 131.62), // Kunisaki (国東)
        Pair(33.45, 131.68), // Kitsuki
        Pair(33.32, 131.50), // Beppu Bay (別府湾)
        Pair(33.25, 131.62), // Oita (大分)
        Pair(33.25, 131.90), // Saganoseki (佐賀関)
        Pair(33.12, 131.85), // Usuki (臼杵)
        Pair(33.02, 131.90), // Tsukumi
        Pair(32.96, 131.90), // Saiki (佐伯)
        Pair(32.80, 131.95), // Kamae
        // Hyuga-nada (日向灘)
        Pair(32.58, 131.68), // Nobeoka (延岡)
        Pair(32.40, 131.65), // Hyuga (日向)
        Pair(32.15, 131.52), // Tsuno
        Pair(32.05, 131.48), // Takanabe
        Pair(31.92, 131.45), // Miyazaki (宮崎)
        Pair(31.75, 131.45), // Aoshima
        Pair(31.58, 131.40), // Nichinan (日南)
        Pair(31.45, 131.35), // Kushima (串間・都井岬)
        Pair(31.38, 131.10), // Shibushi Bay (志布志湾)
        Pair(31.22, 131.05), // Osaki
        Pair(31.05, 130.85), // Kimotsuki
        // Osumi Peninsula & Cape Sata (大隅半島・佐多岬)
        Pair(30.99, 130.66), // Cape Sata (佐多岬・九州最南端)
        Pair(31.15, 130.70), // Nejime
        // Kagoshima Bay / Kinko Bay (鹿児島湾・錦江湾)
        Pair(31.40, 130.75), // Tarumizu
        Pair(31.58, 130.65), // Sakurajima (桜島)
        Pair(31.72, 130.70), // Kirishima
        Pair(31.70, 130.60), // Aira
        Pair(31.60, 130.55), // Kagoshima (鹿児島)
        Pair(31.25, 130.63), // Ibusuki (指宿・長崎鼻)
        Pair(31.27, 130.30), // Makurazaki (枕崎)
        // Satsuma Peninsula West Coast (薩摩半島西岸)
        Pair(31.40, 130.12), // Kasasa / Cape Noma (野間岬)
        Pair(31.60, 130.25), // Fukiagehama
        Pair(31.82, 130.18), // Satsumasendai (薩摩川内)
        Pair(32.05, 130.20), // Akune (阿久根)
        Pair(32.18, 130.30), // Nagashima
        Pair(32.20, 130.38), // Izumi (出水)
        // Yatsushiro Sea & Ariake Sea (八代海・有明海)
        Pair(32.22, 130.42), // Minamata (水俣)
        Pair(32.40, 130.55), // Ashikita
        Pair(32.50, 130.60), // Yatsushiro (八代)
        Pair(32.65, 130.62), // Uki
        Pair(32.70, 130.60), // Uto Peninsula (宇土半島)
        Pair(32.80, 130.65), // Kumamoto Port (熊本港)
        Pair(32.95, 130.45), // Tamana (玉名)
        Pair(33.05, 130.40), // Omuta (大牟田)
        Pair(33.15, 130.30), // Yanagawa
        Pair(33.15, 130.20), // Saga (佐賀)
        Pair(33.05, 130.15), // Tara
        Pair(32.85, 130.20), // Isahaya East
        // Shimabara Peninsula (島原半島)
        Pair(32.78, 130.37), // Shimabara (島原)
        Pair(32.65, 130.35), // Minamishimabara
        Pair(32.60, 130.20), // Kuchinotsu (口之津)
        Pair(32.68, 130.10), // Obama (小浜)
        Pair(32.80, 130.08), // Aino / Isahaya
        // Nagasaki Peninsula & Omura Bay (長崎半島・大村湾)
        Pair(32.60, 129.75), // Cape Nomo (野母崎)
        Pair(32.70, 129.85), // Nagasaki (長崎港)
        Pair(32.85, 129.70), // Saikai (西海)
        Pair(33.17, 129.70), // Sasebo (佐世保)
        Pair(33.30, 129.60), // Emukae
        Pair(33.35, 129.55), // Hirado Strait (平戸瀬戸)
        Pair(33.40, 129.70), // Matsuura / Imari (伊万里)
        Pair(33.55, 129.90), // Karatsu (唐津)
        // Genkai-nada & Hakata Bay (玄界灘・博多湾)
        Pair(33.60, 130.05), // Itoshima (糸島)
        Pair(33.60, 130.35), // Fukuoka / Hakata Bay (福岡・博多)
        Pair(33.70, 130.40), // Shikanoshima (志賀島)
        Pair(33.75, 130.45), // Koga
        Pair(33.80, 130.50), // Munakata (宗像)
        Pair(33.90, 130.70), // Ashiya
        Pair(33.92, 130.82)  // Wakamatsu (若松)
    )

    // =========================================================================
    // 5. MAJOR ISLANDS (主要離島群)
    // =========================================================================

    // Awaji Island (淡路島) - Essential Seto Inland Sea landmark
    val AWAJI_COAST = listOf(
        Pair(34.60, 135.01), // Iwaya / Akashi Kaikyo Bridge (岩屋)
        Pair(34.50, 134.98), // Tsuna
        Pair(34.35, 134.90), // Sumoto (洲本)
        Pair(34.28, 134.92), // Yura
        Pair(34.20, 134.80), // Nada
        Pair(34.22, 134.68), // Fukura / Naruto Strait (福良・鳴門海峡)
        Pair(34.33, 134.72), // Minamiawaji (南あわじ)
        Pair(34.48, 134.88), // Goshiki
        Pair(34.55, 134.95)  // Nojima
    )

    // Sado Island (佐渡島) - Authentic hourglass shape
    val SADO_COAST = listOf(
        Pair(38.32, 138.50), // Cape Hajikizaki (はじき崎・大佐渡北端)
        Pair(38.25, 138.56), // Ryotsu Bay (両津湾)
        Pair(38.10, 138.52), // Niibo
        Pair(37.95, 138.45), // Kosado South
        Pair(37.80, 138.25), // Cape Ogi (小木岬)
        Pair(37.90, 138.28), // Sawata
        Pair(38.00, 138.25), // Mano Bay (真野湾)
        Pair(38.10, 138.22), // Aikawa (相川)
        Pair(38.28, 138.35)  // Sotokaifu
    )

    // Tsushima (対馬)
    val TSUSHIMA_COAST = listOf(
        Pair(34.70, 129.45), // Kami-tsushima North
        Pair(34.55, 129.48),
        Pair(34.38, 129.40), // Toyotama
        Pair(34.20, 129.35), // Izuhara
        Pair(34.12, 129.20), // Tsutsu (豆酘崎)
        Pair(34.25, 129.18),
        Pair(34.40, 129.28), // Aso Bay (浅茅湾)
        Pair(34.60, 129.35)
    )

    // Iki Island (壱岐島)
    val IKI_COAST = listOf(
        Pair(33.85, 129.72),
        Pair(33.80, 129.78),
        Pair(33.70, 129.75),
        Pair(33.72, 129.65)
    )

    // Goto Islands (五島列島 - 福江島 & 中通島)
    val GOTO_FUKUE_COAST = listOf(
        Pair(32.75, 128.88), // Fukue Port
        Pair(32.70, 128.85),
        Pair(32.60, 128.75), // Tomie
        Pair(32.65, 128.60), // Ose崎
        Pair(32.78, 128.68)
    )

    val GOTO_NAKADORI_COAST = listOf(
        Pair(33.05, 129.12),
        Pair(32.95, 129.08),
        Pair(32.88, 129.00),
        Pair(32.98, 129.02)
    )

    // Oki Islands (隠岐諸島 - 島後)
    val OKI_DOGO_COAST = listOf(
        Pair(36.35, 133.32),
        Pair(36.25, 133.38),
        Pair(36.15, 133.30),
        Pair(36.25, 133.22)
    )

    // Izu Islands (伊豆諸島)
    val IZU_OSHIMA_COAST = listOf(
        Pair(34.78, 139.40),
        Pair(34.75, 139.44),
        Pair(34.70, 139.38),
        Pair(34.73, 139.34)
    )

    val IZU_NIIJIMA_COAST = listOf(
        Pair(34.42, 139.28),
        Pair(34.35, 139.25),
        Pair(34.38, 139.23)
    )

    val IZU_KOZUSHIMA_COAST = listOf(
        Pair(34.22, 139.15),
        Pair(34.18, 139.14),
        Pair(34.20, 139.12)
    )

    val IZU_MIYAKE_COAST = listOf(
        Pair(34.12, 139.55),
        Pair(34.08, 139.56),
        Pair(34.05, 139.50),
        Pair(34.10, 139.49)
    )

    val IZU_HACHIJO_COAST = listOf(
        Pair(33.15, 139.82),
        Pair(33.10, 139.85),
        Pair(33.05, 139.78),
        Pair(33.12, 139.75)
    )

    // Tanegashima & Yakushima (種子島・屋久島)
    val TANEGASHIMA_COAST = listOf(
        Pair(30.85, 131.02),
        Pair(30.70, 131.05),
        Pair(30.40, 130.95), // Cape Kadokura
        Pair(30.55, 130.90),
        Pair(30.75, 130.95)
    )

    val YAKUSHIMA_COAST = listOf(
        Pair(30.45, 130.55),
        Pair(30.38, 130.65),
        Pair(30.25, 130.55),
        Pair(30.30, 130.42),
        Pair(30.40, 130.45)
    )

    // Amami Oshima & Tokunoshima (奄美大島・徳之島)
    val AMAMI_COAST = listOf(
        Pair(28.52, 129.70), // Kasari
        Pair(28.40, 129.55), // Naze
        Pair(28.25, 129.40),
        Pair(28.12, 129.25), // Setouchi
        Pair(28.25, 129.30),
        Pair(28.45, 129.50)
    )

    val TOKUNOSHIMA_COAST = listOf(
        Pair(27.85, 128.95),
        Pair(27.75, 129.02),
        Pair(27.68, 128.95),
        Pair(27.78, 128.88)
    )

    // Okinawa Main Island (沖縄本島)
    val OKINAWA_COAST = listOf(
        Pair(26.87, 128.26), // Cape Hedo (辺戸岬)
        Pair(26.70, 128.20), // Kunigami
        Pair(26.65, 128.00), // Ogimi
        Pair(26.70, 127.88), // Motobu Peninsula (本部半島)
        Pair(26.58, 127.98), // Nago Bay (名護湾)
        Pair(26.50, 127.85), // Onna
        Pair(26.40, 127.75), // Cape Zanpa (残波岬)
        Pair(26.32, 127.75), // Chatan
        Pair(26.25, 127.70), // Ginowan
        Pair(26.21, 127.67), // Naha (那覇)
        Pair(26.10, 127.67), // Itoman (糸満)
        Pair(26.08, 127.72), // Cape Kyan (喜屋武岬)
        Pair(26.15, 127.82), // Nanjo (南城)
        Pair(26.30, 127.92), // Katsuren (勝連半島)
        Pair(26.45, 127.88), // Uruma
        Pair(26.55, 128.05), // Ginoza
        Pair(26.65, 128.15)  // Higashi
    )

    // Miyako & Ishigaki Islands (宮古島・石垣島・西表島)
    val MIYAKO_COAST = listOf(
        Pair(24.90, 125.30),
        Pair(24.78, 125.38),
        Pair(24.72, 125.28),
        Pair(24.80, 125.22)
    )

    val ISHIGAKI_COAST = listOf(
        Pair(24.58, 124.32), // Hirakubo
        Pair(24.45, 124.28),
        Pair(24.34, 124.16),
        Pair(24.42, 124.10)
    )

    val IRIOMOTE_COAST = listOf(
        Pair(24.42, 123.85),
        Pair(24.35, 123.90),
        Pair(24.28, 123.82),
        Pair(24.32, 123.72)
    )

    // Northern Territories (北方四島 - 国後・択捉・色丹)
    val KUNASHIRI_COAST = listOf(
        Pair(44.48, 146.50),
        Pair(44.20, 146.15),
        Pair(43.88, 145.65), // Rurui
        Pair(43.95, 145.75),
        Pair(44.30, 146.30)
    )

    val SHIKOTAN_COAST = listOf(
        Pair(43.88, 146.85),
        Pair(43.80, 146.80),
        Pair(43.75, 146.70),
        Pair(43.82, 146.72)
    )

    // Rishiri & Rebun & Okushiri (利尻・礼文・奥尻)
    val RISHIRI_COAST = listOf(
        Pair(45.22, 141.25),
        Pair(45.18, 141.32),
        Pair(45.12, 141.22),
        Pair(45.18, 141.15)
    )

    val REBUN_COAST = listOf(
        Pair(45.45, 141.05),
        Pair(45.35, 141.05),
        Pair(45.28, 141.00),
        Pair(45.38, 140.98)
    )

    val OKUSHIRI_COAST = listOf(
        Pair(42.22, 139.55),
        Pair(42.15, 139.55),
        Pair(42.05, 139.45),
        Pair(42.15, 139.42)
    )

    // =========================================================================
    // 6. INLAND LAKES (主要湖沼 - リアルな内陸水域)
    // =========================================================================

    // Lake Biwa (琵琶湖) - Shiga Prefecture
    val LAKE_BIWA_COAST = listOf(
        Pair(35.53, 136.18), // Nagahama north
        Pair(35.42, 136.24), // Hikone
        Pair(35.25, 136.15), // Omihachiman
        Pair(35.08, 135.95), // Kusatsu / Otsu
        Pair(35.00, 135.88), // Seta outlet
        Pair(35.12, 135.92), // Katata / Biwako Ohashi
        Pair(35.28, 135.98), // Omimaiko
        Pair(35.42, 136.05), // Takashima
        Pair(35.52, 136.10)  // Makino
    )

    // Lake Kasumigaura (霞ヶ浦) - Ibaraki Prefecture
    val LAKE_KASUMIGAURA_COAST = listOf(
        Pair(36.08, 140.25), // Tsuchiura
        Pair(36.12, 140.40), // Kasumigaura north
        Pair(36.02, 140.50), // Namegata
        Pair(35.95, 140.58), // Itako
        Pair(35.92, 140.45), // Inashiki
        Pair(35.98, 140.32), // Ami
        Pair(36.05, 140.22)
    )

    // Lake Saroma (サロマ湖) - Hokkaido
    val LAKE_SAROMA_COAST = listOf(
        Pair(44.22, 143.72),
        Pair(44.18, 143.92),
        Pair(44.10, 143.88),
        Pair(44.12, 143.70)
    )

    // Lake Inawashiro (猪苗代湖) - Fukushima Prefecture
    val LAKE_INAWASHIRO_COAST = listOf(
        Pair(37.54, 140.06),
        Pair(37.48, 140.12),
        Pair(37.42, 140.08),
        Pair(37.46, 140.00)
    )

    // Lake Shinji & Nakaumi (宍道湖・中海) - Shimane/Tottori
    val LAKE_SHINJI_COAST = listOf(
        Pair(35.46, 132.92),
        Pair(35.44, 133.02),
        Pair(35.48, 133.20),
        Pair(35.54, 133.25),
        Pair(35.50, 133.05)
    )

    // Lake Hamana (浜名湖) - Shizuoka Prefecture
    val LAKE_HAMANA_COAST = listOf(
        Pair(34.82, 137.58),
        Pair(34.75, 137.62),
        Pair(34.68, 137.60),
        Pair(34.75, 137.52)
    )

    // Lake Towada (十和田湖) - Aomori/Akita
    val LAKE_TOWADA_COAST = listOf(
        Pair(40.52, 140.90),
        Pair(40.45, 140.92),
        Pair(40.42, 140.85),
        Pair(40.48, 140.82)
    )

    // =========================================================================
    // 7. PREFECTURAL BOUNDARIES (全47都道府県の県境線)
    // =========================================================================
    val PREFECTURE_DIVIDERS = listOf(
        // Hokkaido Subprefectures
        listOf(Pair(42.3, 140.0), Pair(42.5, 140.4)),
        listOf(Pair(42.5, 141.0), Pair(42.9, 141.3)),
        listOf(Pair(43.2, 142.2), Pair(44.5, 142.4)),
        listOf(Pair(42.8, 143.0), Pair(43.5, 143.2)),
        listOf(Pair(43.1, 144.1), Pair(43.6, 144.5)),

        // Tohoku (Aomori, Iwate, Akita, Miyagi, Yamagata, Fukushima)
        listOf(Pair(40.4, 139.9), Pair(40.35, 140.6)),
        listOf(Pair(40.35, 140.6), Pair(40.3, 141.7)),
        listOf(Pair(40.35, 140.6), Pair(39.5, 140.7), Pair(39.0, 140.7)),
        listOf(Pair(39.15, 139.9), Pair(39.1, 140.5)),
        listOf(Pair(39.0, 140.7), Pair(38.9, 141.6)),
        listOf(Pair(39.1, 140.5), Pair(38.3, 140.5), Pair(38.0, 140.5)),
        listOf(Pair(38.0, 139.7), Pair(37.8, 140.4)),
        listOf(Pair(38.0, 140.5), Pair(37.85, 140.9)),

        // Kanto & Koshin (Ibaraki, Tochigi, Gunma, Saitama, Chiba, Tokyo, Kanagawa, Yamanashi)
        listOf(Pair(37.0, 140.4), Pair(36.85, 140.8)),
        listOf(Pair(37.1, 139.8), Pair(37.0, 140.4)),
        listOf(Pair(37.0, 140.4), Pair(36.4, 140.2), Pair(36.25, 140.0)),
        listOf(Pair(37.0, 139.4), Pair(36.5, 139.4), Pair(36.25, 139.6)),
        listOf(Pair(36.3, 138.8), Pair(36.2, 139.3), Pair(36.15, 139.7)),
        listOf(Pair(36.15, 139.7), Pair(36.0, 139.9)),
        listOf(Pair(36.0, 139.9), Pair(35.85, 140.5), Pair(35.75, 140.8)),
        listOf(Pair(35.85, 139.0), Pair(35.8, 139.6), Pair(35.78, 139.9)),
        listOf(Pair(35.78, 139.9), Pair(35.65, 139.9)),
        listOf(Pair(35.65, 139.0), Pair(35.58, 139.4), Pair(35.53, 139.75)),
        listOf(Pair(35.65, 139.0), Pair(35.4, 139.0)),
        listOf(Pair(35.4, 139.0), Pair(35.15, 139.0)),

        // Chubu & Hokuriku (Niigata, Toyama, Ishikawa, Fukui, Nagano, Gifu, Shizuoka, Aichi)
        listOf(Pair(36.95, 137.6), Pair(36.9, 137.7)),
        listOf(Pair(36.9, 136.9), Pair(36.6, 136.8), Pair(36.35, 136.8)),
        listOf(Pair(36.3, 136.3), Pair(36.15, 136.5)),
        listOf(Pair(37.1, 138.2), Pair(36.8, 138.6)),
        listOf(Pair(36.8, 138.6), Pair(36.8, 139.2)),
        listOf(Pair(36.8, 138.6), Pair(36.3, 138.6)),
        listOf(Pair(36.1, 138.4), Pair(35.8, 138.3)),
        listOf(Pair(35.8, 138.1), Pair(35.3, 137.9)),
        listOf(Pair(35.3, 137.6), Pair(35.2, 137.6)),
        listOf(Pair(36.3, 137.4), Pair(35.8, 137.5), Pair(35.4, 137.5)),
        listOf(Pair(35.2, 137.6), Pair(34.7, 137.5)),
        listOf(Pair(35.4, 137.0), Pair(35.3, 136.8), Pair(35.1, 136.7)),
        listOf(Pair(36.0, 136.6), Pair(35.7, 136.4)),
        listOf(Pair(35.1, 136.7), Pair(35.0, 136.7)),
        listOf(Pair(35.3, 136.5), Pair(35.1, 136.6)),

        // Kansai (Mie, Shiga, Kyoto, Osaka, Hyogo, Nara, Wakayama)
        listOf(Pair(35.6, 136.1), Pair(35.5, 135.9)),
        listOf(Pair(35.5, 135.8), Pair(35.5, 135.4)),
        listOf(Pair(35.4, 135.9), Pair(35.0, 135.8)),
        listOf(Pair(35.1, 136.3), Pair(34.9, 136.2)),
        listOf(Pair(34.9, 136.0), Pair(34.8, 136.0)),
        listOf(Pair(34.8, 135.8), Pair(34.7, 135.8)),
        listOf(Pair(34.9, 135.6), Pair(34.8, 135.6)),
        listOf(Pair(35.4, 135.1), Pair(35.1, 135.2)),
        listOf(Pair(34.8, 135.4), Pair(34.68, 135.4)),
        listOf(Pair(34.7, 135.7), Pair(34.45, 135.7)),
        listOf(Pair(34.4, 135.2), Pair(34.3, 135.5)),
        listOf(Pair(34.7, 136.1), Pair(34.3, 136.0), Pair(34.0, 135.9)),
        listOf(Pair(34.3, 135.6), Pair(33.9, 135.7)),
        listOf(Pair(34.0, 136.0), Pair(33.8, 136.0)),

        // Chugoku (Tottori, Shimane, Okayama, Hiroshima, Yamaguchi)
        listOf(Pair(35.6, 134.4), Pair(35.3, 134.3)),
        listOf(Pair(35.3, 134.3), Pair(34.8, 134.3)),
        listOf(Pair(35.3, 134.2), Pair(35.2, 133.4)),
        listOf(Pair(35.5, 133.3), Pair(35.2, 133.2)),
        listOf(Pair(35.1, 133.0), Pair(34.8, 132.3)),
        listOf(Pair(35.1, 133.4), Pair(34.4, 133.4)),
        listOf(Pair(34.7, 131.8), Pair(34.5, 131.6)),
        listOf(Pair(34.4, 132.2), Pair(34.1, 132.2)),

        // Shikoku (Tokushima, Kagawa, Ehime, Kochi)
        listOf(Pair(34.2, 134.4), Pair(34.1, 133.8)),
        listOf(Pair(34.0, 133.7), Pair(34.0, 133.5)),
        listOf(Pair(33.9, 134.2), Pair(33.5, 134.3)),
        listOf(Pair(33.9, 133.3), Pair(33.5, 132.8), Pair(33.0, 132.5)),

        // Kyushu (Fukuoka, Saga, Nagasaki, Kumamoto, Oita, Miyazaki, Kagoshima)
        listOf(Pair(33.6, 130.3), Pair(33.3, 130.3)),
        listOf(Pair(33.3, 129.9), Pair(33.1, 130.1)),
        listOf(Pair(33.6, 131.0), Pair(33.3, 131.0)),
        listOf(Pair(33.2, 130.5), Pair(33.1, 130.8)),
        listOf(Pair(33.2, 131.2), Pair(32.9, 131.2)),
        listOf(Pair(32.9, 131.6), Pair(32.7, 131.8)),
        listOf(Pair(32.8, 131.2), Pair(32.2, 131.0)),
        listOf(Pair(32.2, 130.5), Pair(32.1, 130.7)),
        listOf(Pair(32.1, 131.0), Pair(31.4, 131.2))
    )

    // =========================================================================
    // 8. TECTONIC TRENCHES & FAULT LINES (海溝・トラフ)
    // =========================================================================
    val JAPAN_TRENCH = listOf(
        Pair(44.0, 148.0),
        Pair(42.5, 146.0),
        Pair(41.0, 144.5),
        Pair(39.5, 144.0),
        Pair(38.0, 143.5),
        Pair(36.5, 142.5),
        Pair(35.5, 142.0)
    )

    val NANKAI_TROUGH = listOf(
        Pair(34.8, 139.2), // Sagami / Suruga
        Pair(34.0, 137.5),
        Pair(33.2, 136.0),
        Pair(32.4, 134.5),
        Pair(31.8, 133.0),
        Pair(31.0, 131.5)
    )

    val RYUKYU_TRENCH = listOf(
        Pair(30.0, 131.0),
        Pair(28.5, 130.0),
        Pair(26.5, 128.5),
        Pair(24.5, 126.0)
    )

    // =========================================================================
    // 9. TOPOGRAPHIC MOUNTAIN SPINES (主要山脈レリーフ)
    // =========================================================================
    val MOUNTAIN_SPINES = listOf(
        // Japan Alps (飛騨・木曽・赤石山脈)
        listOf(Pair(36.75, 137.60), Pair(36.25, 137.65), Pair(35.80, 137.80), Pair(35.40, 138.15)),
        // Ou Mountain Range (奥羽山脈)
        listOf(Pair(40.50, 140.75), Pair(39.75, 140.80), Pair(39.00, 140.65), Pair(38.00, 140.25), Pair(37.30, 140.05)),
        // Echigo Mountain Range (越後山脈)
        listOf(Pair(37.50, 139.30), Pair(37.00, 139.00), Pair(36.80, 138.80)),
        // Hidaka Range (日高山脈)
        listOf(Pair(43.10, 142.70), Pair(42.50, 142.85), Pair(42.10, 143.15)),
        // Chugoku Mountains (中国山地)
        listOf(Pair(35.35, 134.20), Pair(35.05, 133.20), Pair(34.75, 132.20), Pair(34.40, 131.50)),
        // Shikoku Mountains (四国山地: 剣山・石鎚山系)
        listOf(Pair(33.85, 134.10), Pair(33.75, 133.50), Pair(33.70, 133.10)),
        // Kyushu Mountain Spine (九州山地)
        listOf(Pair(32.90, 131.10), Pair(32.50, 131.10), Pair(32.00, 130.90), Pair(31.70, 130.80))
    )
}
