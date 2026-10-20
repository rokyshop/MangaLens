# MangaLens Android — prototype V0.1

Application Android native permettant de lire des sites **HTTPS** dans un navigateur intégré et de traduire le texte de la partie visible d'un manga/manhwa/manhua **sans télécharger manuellement les images**.

**État : projet source fonctionnel conçu pour Android Studio, mais compilation APK et essais sur téléphone non réalisés dans l'environnement de création.** La reconstruction de fonds est expérimentale et peut rendre les images détaillées imparfaitement. C'est un prototype initial, **pas une application finalisée**.

## Démarrer avec Android Studio (Windows)

1. Installer **Android Studio** et son SDK Android 35. Avoir une connexion Internet pour les dépendances Gradle.
2. Décompresser le ZIP puis utiliser **File > Open** sur le dossier `MangaLensAndroid` (le dossier contenant `settings.gradle.kts`).
3. Lancer la synchronisation **Gradle Sync** avec **JDK 17 ou 21**. Le projet utilise Android Gradle Plugin **8.13.2**, Kotlin **2.2.20** et Gradle **8.13**. La configuration de distribution se trouve dans `gradle/wrapper/gradle-wrapper.properties`.
4. Si Android Studio demande un *Gradle Wrapper JAR* (absent de cette archive hors ligne), installer Gradle 8.13 ou utiliser son Gradle local, puis lancer **`gradle wrapper --gradle-version 8.13`** à la racine pour générer les scripts et le JAR standard. Re-synchroniser.
5. Brancher un appareil Android (Android 8.0+, API 26) avec le débogage USB activé, sélectionner l'appareil puis **Run ▶**.
6. Pour fabriquer un APK : **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Fichier attendu après compilation : `app/build/outputs/apk/debug/app-debug.apk`.

**Aucun APK pré-compilé n'est inclus** : ni SDK Android, ni dépendances Android Gradle ne sont accessibles à cette session de développement. Les tests Android/ML Kit devront être exécutés sur appareil.

## Tester la démonstration

1. À l'ouverture, une page HTML de démonstration apparaît avec des dialogues anglais.
2. Sélectionner **Anglais** → **Français**, puis appuyer sur **Traduire**.
3. Au premier essai, consentir au téléchargement du modèle de traduction (Wi-Fi ou données mobiles). Google indique qu'un modèle linguistique est approximativement de **30 Mo**.
4. Observer les dialogues remplacés. Le bouton **Voir l'original** permet de basculer l'affichage.
5. Activer **Auto-défilement**, puis descendre : la traduction est recalculée après une pause d'environ une seconde.
6. Pour un vrai chapitre, saisir l'adresse d'un site **HTTPS** dans le champ en haut.

## Fonctionnalités incluses

- WebView Android ; barre d'adresse, retour, page de démonstration intégrée.
- OCR ML Kit : latin (anglais/français/espagnol), japonais, coréen, chinois (système d'écriture choisi manuellement).
- Traduction ML Kit sur appareil vers français, anglais, espagnol, allemand, italien ou portugais.
- Autorisation explicite du premier téléchargement ; choix Wi-Fi uniquement ou données mobiles.
- Capture uniquement de la zone visible avec PixelCopy, sans contourner CORS ni lire les cookies du navigateur.
- Rectangle de texte détecté → interpolation locale du fond → texte traduit ajusté à l'espace disponible.
- Texte noir ou blanc selon la luminosité du fond.
- Traduction manuelle ou rebalayage automatique après défilement et changement de page.
- Mémoire LRU de 500 traductions et arrêt de l'ancien traitement si la page change.
- Aucune clé de serveur ni compte requis ; pas de permission stockage ou capture globale de l'écran.

## Limitations connues

- **Ce n'est pas un inpainting par IA** : les aplats et dégradés simples sont interpolés, mais une zone avec du dessin, du hachurage ou un personnage derrière le texte peut devenir floue ou incorrecte.
- **Pas de qualité scanlation garantie** : les traductions ne tiennent pas encore compte des dialogues précédents ni des caractères de personnages ; elles peuvent être littérales.
- OCR et texte rendu sont basés sur la **capture d'écran** : de petits caractères, du texte stylisé, vertical ou un long chapitre peuvent donner de mauvais résultats. Le système de lecture ne traduit que ce qui est visible.
- Mode **source automatique**, découpage en bulles plus fin, traduction du chapitre entier, édition manuelle et export d'image non inclus.
- Sur certains sites, la WebView peut être bloquée, les images peuvent être protégées, ou les pages peuvent se comporter différemment de Chrome. Aucun contournement anti-bot/DRM n'est prévu.
- Les modèles ML Kit doivent être téléchargés avant la première traduction pour chaque nouvelle langue ; avec le mode Wi-Fi seulement, ils attendent l'accès Wi-Fi.
- L'interface peut être perfectible sur écrans étroits et paysages ; validation en situation réelle nécessaire.

## Vie privée et précautions

L'application utilise uniquement la permission Android `INTERNET` pour afficher les sites HTTPS et charger les modèles Google. La reconnaissance et la traduction sont locales une fois les modèles installés. Aucun serveur de collecte ou clé API n'est ajouté. La navigation WebView charge les ressources des sites consultés normalement ; les politiques de confidentialité de ces sites restent applicables.

Les sites tiers peuvent avoir des conditions d'utilisation et des droits d'auteur concernant leurs images. Utiliser l'application pour la lecture personnelle et ne pas redistribuer des pages modifiées sans autorisation.

## Tests réalisables sans SDK Android

Le module mathématique de reconstruction peut être testé avec un `kotlinc` installé localement :

```bash
bash tests/run-border-blend-test.sh
```

Les tests intégrés de l'algorithme vérifient les aplats, le mélange des couleurs et les poids de proximité. Ils **ne remplacent pas** une compilation Android ni un test OCR réel.

Consulter **ARCHITECTURE.md** pour les modules et la feuille de route.
