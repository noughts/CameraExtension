RESOLVED

- ANDROID_STILL_IMAGE_QUALITY_BESTが正しく動作しない場合がある不具合の修正
- 要求されたプレビュー画像サイズと実際のプレビューサイズが一致しない不具合の修正
（最適プレビュー画像選択アルゴリズムのバグ）
- ANDROID_STILL_IMAGE_QUALITY_DEFAULTが動作しない不具合
-> DEFAULT自体が未定義、QUALITY_LOWに変更

IN PROGRESS

- FREObjectの戻り値に含まれるw,hの値が不正
->途中でAndroid側が落ちるせい（メモリ不足）
- Galaxy NexusでstartPreviewに失敗
- 最大画像サイズにして撮影した際にAndroid側で落ちる、おそらくメモリが足りてない
- CameraFlip時に落ちる

- CameraSurfaceの挙動
Threadを立ててプレビュー画像がくるまで待機
画像がきたらメンバのバイト配列に格納、スレッドを起こす
起こされたスレッドはprocessFrameを呼び画像をNV21からRGBAに変換
processFrameで変換する前のYUV画像は正しく送られてきている（JPEGに変換して確認済み）
JNI経由で変換したビットマップ(RGBA)をARGBに変換してJPEGに書き出し -> 正しいビットマップのよう
-> 結局サンプルアプリ側の問題と判明。

Android Camera
- プレビュー画面の画像の向きはsetDisplayOrientationで規定
-> ただしコールバックにくる画像の向きは変わらないので自分で回転させる必要あり
- 撮影時の画像の向きはParameters.orientationで規定（JPEGのEXIFに情報が入る）

captureAndSaveImage()の実装
- ディレクトリ名を指定すると、Pictures/ディレクトリ名/IMG_YYYYMMDD_hhmmss.jpgというファイル名で保存する。
- 戻り値にファイルパスを返す。ただしこのパスはSTILL_IMAGE_READYイベントが飛んでくるまでは有効ではない。
- 保存時に画像の向きを指定する。デフォルトがポートレート（端末の上辺が左に来る形で持つ状態）。

PackagingErrorが頻発
-> Debug用に作成したiOSのターゲットを削除してリビルド