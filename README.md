# MangaLens Android V0.3

Navigateur Android pour traduire les dialogues de mangas et manhwas pendant la lecture.

## Changements

- Blocage de domaines publicitaires connus, activé par défaut (bouton Anti-pub).
- Traduction continue activée par défaut. Les bulles traduites suivent le défilement ; un simple toucher ne les efface plus.
- Surveillance du chargement tardif des images visibles.
- OCR ML Kit en plusieurs passes avec agrandissement et tuiles chevauchantes pour les petits caractères.
- Mode Qualité IA facultatif : traduction contextuelle avec Gemini 2.5 Flash et une clé personnelle chiffrée via Android Keystore. Appui long sur le bouton pour modifier ou supprimer la clé.
- Mode local disponible sans clé. Si l’IA échoue, repli sur la traduction locale.
- Cache des fonds limité à 24 Mio.

## Compiler et installer

Le workflow GitHub Actions « Compiler l’APK Android » compile automatiquement les commits sur main. Ouvrir la dernière exécution réussie, puis télécharger l’artefact MangaLens-APK-debug. Extraire et installer app-debug.apk sur le téléphone.

Version : 0.3.0, versionCode 3. Les APK debug de runners différents peuvent avoir des signatures différentes : Android peut alors demander de désinstaller l’ancienne version avant installation (les préférences sont perdues).

## Utilisation et limites

Choisir les langues, ouvrir un chapitre HTTPS, laisser « Trad. continue » activée et faire défiler. « Traduire » lance une analyse immédiate ; « Voir l’original » bascule l’affichage.

La traduction est progressive sur les parties visibles, sans prétraduction complète du chapitre hors écran. Les polices complexes peuvent rester mal reconnues. Les pages avec défilement dans un élément interne nécessitent une nouvelle analyse après le geste. Le blocage publicitaire repose sur une liste de domaines et ne couvre pas toutes les publicités. La reconstruction du fond est locale et expérimentale.

Le mode IA transmet uniquement les textes reconnus à Google, sans images ni cookies ; Internet et quota API sont nécessaires et des frais peuvent s’appliquer. La traduction locale fonctionne sur l’appareil après téléchargement des modèles.

Une compilation réussie ne remplace pas un essai sur téléphone et sur des chapitres réels.
