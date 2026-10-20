# Fabriquer l'APK sans installer Android Studio

Le projet contient maintenant un workflow GitHub Actions qui compile un APK de test sur les serveurs GitHub. Cette archive ne contient pas encore d'APK, car aucune compilation Android n'a pu etre effectuee dans cet environnement.

## Procedure

1. Se connecter a GitHub et creer un nouveau depot (`MangaLens`, prive ou public).
2. Ajouter **tous les fichiers** du dossier `MangaLensAndroid` a la racine du depot, **y compris le dossier cache `.github/workflows/`**. Pour une premiere importation, Git sur PC est souvent plus pratique qu'un navigateur mobile.
3. Dans GitHub, ouvrir l'onglet **Actions**. Si demande, autoriser les workflows de ce depot.
4. Selectionner **Compiler l'APK Android**, puis **Run workflow** (ou utiliser le lancement automatique a chaque push sur `main`/`master`).
5. Si le job reussit, ouvrir son resultat puis, tout en bas, **Artifacts > MangaLens-APK-debug**.
6. Telecharger l'archive d'artifact, l'extraire et transferer **`app-debug.apk`** sur le telephone.
7. Sur Android, autoriser si necessaire l'installation depuis l'application qui ouvre l'APK, puis installer MangaLens.

Ce processus **necessite Internet et un compte GitHub**, mais pas Android Studio. GitHub peut imposer des quotas ou des verifications de compte. L'APK debug est signe automatiquement par le systeme de build Android ; il n'est pas destine a une publication sur le Play Store.

## Important

La compilation n'a pas ete executee ici : des problemes de compatibilite pourraient etre detectes seulement lors de l'execution du workflow. Consulter les logs de l'onglet Actions en cas d'erreur.
