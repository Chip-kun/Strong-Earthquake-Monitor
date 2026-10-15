# 強震モニター (Kyoshin Monitor & Earthquake Tracker)

<p align="center">
  <img src="screenshot_kmoni.png" width="280" alt="強震モニタ画面" />
  <img src="screenshot_history.png" width="280" alt="地震履歴画面" />
  <img src="screenshot_sensor.png" width="280" alt="マイ地震計画面" />
</p>

「強震モニター」は、リアルタイムの強震モニタ画像、気象庁およびP2P地震情報APIによる地震履歴・緊急地震速報（EEW）の受信、デバイスの加速度センサーを用いた簡易マイ地震計、そして充実した音声読み上げ・防災訓練機能を提供するAndroid向け総合地震・防災アプリです。

最新の **Jetpack Compose** と **Material Design 3** を採用し、直感的で洗練されたUIと確実な速報通知を実現しています。

---

## 📥 APKダウンロード画面 (Download APK)

Android実機に直接インストールしてご利用いただける最新デバッグAPK（**v1.0.4**）の配布情報です。  
スマートフォンから直接ダウンロードされる方は「**APK直接ダウンロード**」ボタンをタップしてください。PC等からご覧の方は、スマートフォンカメラで右側のQRコードをスキャンしてダウンロードできます。

<div align="center">

| 📲 最新リリース: **v1.0.4** (デバッグ版) | 📷 スマホ用スキャンQRコード |
| :---: | :---: |
| <br/>[![Download Kyoshin Monitor APK](https://img.shields.io/badge/📥_APK直接ダウンロード-KyoshinMonitor--debug.apk_(v1.0.4)-00C853?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Chip-kun/Strong-Earthquake-Monitor/releases/download/V1.0.4/KyoshinMonitor-debug.apk)<br/><br/>**[🔗 https://github.com/Chip-kun/Strong-Earthquake-Monitor/releases/download/V1.0.4/KyoshinMonitor-debug.apk](https://github.com/Chip-kun/Strong-Earthquake-Monitor/releases/download/V1.0.4/KyoshinMonitor-debug.apk)**<br/><br/>📁 **ファイル名**: `KyoshinMonitor-debug.apk`<br/>🔖 **バージョン**: `v1.0.4`<br/>🤖 **対応OS**: Android 8.0（API 26）以上<br/>⚡ **主な新機能**: 過去震源地Room履歴マップ、常駐バックグラウンド地震監視サービス（アプリ終了後も通知）、読み上げ速度微調整＆即時テスト発声<br/><br/> | <br/><img src="https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=https%3A%2F%2Fgithub.com%2FChip-kun%2FStrong-Earthquake-Monitor%2Freleases%2Fdownload%2FV1.0.4%2FKyoshinMonitor-debug.apk" width="180" height="180" alt="APKダウンロード用QRコード" /><br/><sub>スマートフォンのカメラで読み取ってダウンロード</sub><br/><br/> |

</div>

### 📲 インストール手順 (簡単3ステップ)

1. **APKのダウンロード**:
   - 上記の「**[APK直接ダウンロード](https://github.com/Chip-kun/Strong-Earthquake-Monitor/releases/download/V1.0.4/KyoshinMonitor-debug.apk)**」リンクまたはQRコードから `KyoshinMonitor-debug.apk` をダウンロードします。
2. **提供元不明のアプリの許可**:
   - Chrome等のブラウザからダウンロード後、インストール時に「提供元不明のアプリ」または「この提供元からのアプリを許可」と表示された場合は、画面の案内に従ってトグルを「**許可（ON）**」に設定してください。
3. **インストール完了と権限設定**:
   - 「**インストール**」をタップして完了したらアプリを起動します。
   - 初回起動時に**通知の許可**（緊急地震速報の通知用）および**位置情報の許可**（震源地からの距離算出用）を「許可」にしてご利用ください。

> ℹ️ **Google Playプロテクトの警告が表示された場合**:  
> 本APKはGitHub Releaseから直接配布されるデバッグ署名済みバイナリです。「Playプロテクトによりブロックされました」と表示される場合は、「**詳細**」をタップし「**このままインストールする（安全ではないアプリをインストール）**」を選択して進めてください。

---

## 🌟 主な機能

### 1. 24時間 常駐バックグラウンド監視 (`Foreground Service`)
- **アプリ終了時・画面スリープ時も確実に通知**: Android Foreground Serviceを採用し、アプリを閉じた状態でも24時間リアルタイムに気象庁・P2P地震速報を監視。
- **高優先度ヘッズアップ通知**: 地震発生や緊急地震速報の検知時に即座に大音量チャイム・バイブレーション・ヘッズアップ通知を発令。
- **音声自動読み上げ**: 画面を開いていなくても、設定した読み上げ速度で震源地や最大震度を音声アナウンス。
- **端末再起動後の自動復旧**: 端末の再起動時にも自動的に監視サービスが立ち上がります。

### 2. リアルタイム強震モニタ (`#kmoni`)
- 防災科学技術研究所（NIED）の強震モニタ画像をリアルタイムに取得・表示。
- リアルタイムアニメーションや画像更新により、日本全国の揺れの伝播状況を視覚的に把握できます。

### 2. 緊急地震速報 (EEW) & 詳細音声アナウンス
- 緊急地震速報（警報・予報）の受信時にアラートバナーを表示し、チャイム音とともに詳細な音声読み上げ（TTS）を実施。
- 発生時刻、震源地、深さ、マグニチュード、推定最大震度、および影響を受ける対象地域（都道府県・市町村）をきめ細かくアナウンスします。

### 3. 地震履歴・詳細レポート (P2P地震情報連携 & Room永続化)
- 過去の地震発生履歴を一覧表示。各地震をタップすると、震度ごとの観測市区町村リストや津波情報の有無まで網羅した詳細レポートを確認できます。
- 地震詳細や履歴項目からも、詳細な音声読み上げをワンタップで実行可能。

### 4. 過去震源地 履歴マップ機能 (Roomローカルデータベース連携)
- Roomデータベースに蓄積された過去の地震・EEW速報ログを元に、日本地図上に過去の震源地をグラフィカルにプロット。
- ピンチズームやドラッグ操作に対応し、マグニチュード規模（円のサイズ）および最大観測震度（色分け）を視覚的に振り返ることが可能。
- 震源マーカーをタップすると、発生日時、深さ、マグニチュード、現在地からの距離、警戒地域などを即座にインスペクション表示＆音声読み上げ。
- 規模（M5+ / M6+ / M7+）や震度（震度4+ / 5弱+ / 6弱+）、警報フラグによるフィルタリング機能および地図スタイル切り替え（衛星地形/レーダー/海溝プレート）を完備。

### 5. 簡易マイ地震計 (アクセロメータ活用)
- スマートフォンの内蔵加速度センサーを利用して、周囲の揺れや微動をリアルタイムに検知・グラフ化。
- 揺れの大きさや波形を視覚的に確認できます。

### 6. 緊急地震速報・防災訓練モード (`Drill`)
- 緊急地震速報のシミュレーション訓練をいつでも発令可能。実際の警戒音や音声読み上げの流れを体験し、万が一のときに備えることができます。

### 7. 防災ガイド & 設定
- 地震発生時の心得や避難手順をまとめた防災ガイドを搭載。
- 音声読み上げの有効/無効、詳細度（簡略/詳細）などのカスタマイズが可能。

---

## 📱 スクリーンショット

| 強震モニタ | 地震履歴 | マイ地震計 |
| :---: | :---: | :---: |
| <img src="screenshot_kmoni.png" width="220" /> | <img src="screenshot_history.png" width="220" /> | <img src="screenshot_sensor.png" width="220" /> |

| 訓練モード | 操作マニュアル |
| :---: | :---: |
| <img src="screenshot_training.png" width="220" /> | <img src="screenshot_manual.png" width="220" /> |

---

## 🛠 技術スタック

- **言語**: Kotlin
- **UIフレームワーク**: Jetpack Compose (Material Design 3)
- **非同期処理**: Kotlin Coroutines & Flow
- **ネットワーク**: Retrofit / OkHttp (P2P地震情報API / 強震モニタ画像取得)
- **ローカルデータベース**: Room Database (地震通知ログ・設定の永続化)
- **音声合成**: Android TextToSpeech (TTS) + カスタムVoiceAlertManager
- **センサー**: Android Sensor API (加速度センサー)
- **アーキテクチャ**: MVVM (Model-View-ViewModel)

---

## 🚀 ビルド・開発方法

Android Studio（Hedgehog以降推奨）またはGradleコマンドを使用してビルドできます。

### 前提条件
- Android SDK (API 34以上推奨)
- Kotlin 1.9+

### ビルドコマンド
```bash
# デバッグAPKのビルド
gradle assembleDebug

# 単体テストの実行
gradle :app:testDebugUnitTest
```

---

## 📄 ライセンス

本アプリケーションはオープンソースとして公開されています。詳細についてはリポジトリ内のライセンスファイルをご確認ください。

---

> **免責事項**: 本アプリは防災科学技術研究所の強震モニタおよび各種オープンAPIの情報を利用して提供するものです。回線状況やサーバーのメンテナンス等により、速報の遅延や表示エラーが発生する場合があります。公式の気象庁発表情報や自治体からの指示を必ずご確認ください。
