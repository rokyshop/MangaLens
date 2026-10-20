# MangaLens — architecture technique

## Objectif V0.1

Lecture de contenus autorisés dans une WebView native, traduction du texte visible et recouvrement des caractères originaux avec texte cible. Pas de téléchargement direct des images, pas de serveur obligatoire.

```text
+---------------- Android Activity / UI ----------------+
|  Adresse HTTPS   Source/Texte cible   Auto  Original  |
|                                                        |
|      WebView (HTML, images, scroll, interaction)        |
|           |                                            |
|      PixelCopy (viewport uniquement)                   |
|           | Bitmap RGB                                 |
|       OcrEngine                                        |
|           | TextBlock + rectangle                      |
|    OfflineTranslator (ML Kit)                          |
|           | Traductions                               |
|       EdgeInpainter → patch local                       |
|           |                                            |
|    TranslationOverlayView (Canvas + StaticLayout)       |
+--------------------------------------------------------+
```

## Sources

| Module | Rôle |
|---|---|
| `MainActivity.kt` | Interface, navigation sécurisée, génération de scan, synchronisation avec scroll |
| `scan/ScreenCapture.kt` | PixelCopy du WebView visible avec solution de repli `draw()` |
| `ocr/OcrEngine.kt` | Text Recognition v2, un modèle OCR par écriture, rectangles des blocs |
| `translate/OfflineTranslator.kt` | Téléchargement consensuel, traduction locale, mise en cache, nettoyage clients |
| `overlay/EdgeInpainter.kt` | Reconstruction par les pixels du contour d'une zone |
| `overlay/BorderBlend.kt` | Interpolation des couleurs sans dépendance Android, testable indépendamment |
| `overlay/TranslationOverlayView.kt` | Patch et traduction sur Canvas ; reflow natif via StaticLayout |
| `model/Language.kt` | Langues prises en charge et scripts OCR |
| `model/TextRegion.kt` | Région de texte et traduction |

## Performance et isolation

- Limite de 32 blocs OCR à la fois (viewport seulement).
- Réduire les appels `Bitmap.getPixel` par prélecture des 4 contours de chaque zone.
- Garde de génération : dès qu'un scroll/page/langue change, les retours d'une ancienne analyse sont ignorés.
- Lecture du site dans WebView : aucune interface JavaScript Android exposée, aucune désactivation de vérification TLS/SSL, pas de fichiers locaux, HTTPS obligatoire.
- Un seul processus d'OCR + traduction actif par fenêtre ; traitement de blocs séquentiel et cache des textes déjà traduits.
- L'overlay n'intercepte pas les gestes du navigateur.

## Version 0.2 proposée

1. Ajuster la position des bulles sur des vrais sites et ajouter tests instrumentés WebView/PixelCopy.
2. Regrouper les petites lignes OCR dans une même bulle et traiter les caractères verticaux.
3. Introduire reconnaissance automatique des scripts, filtrage des éléments du site hors pages manga.
4. Ajouter vrai suivi du scroll des SPA et conteneurs imbriqués, et cache de patches par URL/image/région.
5. Ajouter option pour corriger une traduction et préserver l'historique de dialogue du chapitre (localement).

## Version 0.3 proposée — reconstruction complexe

Pipeline facultatif : détection plus précise du masque des caractères → classification fond uniforme ou complexe → pour les fonds complexes uniquement, moteur local ou service d'**inpainting d'image**. Si un service distant est utilisé, demander un consentement séparé, limiter les images transmises à des recadrages, éviter tout cookie ou identifiant de site, ne jamais intégrer de clé API secrète dans l'application, documenter les coûts et respecter les droits d'auteur.

## Références officielles

- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://developers.google.com/ml-kit/language/translation/android
- https://developer.android.com/reference/android/view/PixelCopy
- https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
