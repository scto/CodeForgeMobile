bash ./gradlew :app:detekt --dry-run
bash ./gradlew :app:diffVersionCatalogs
bash ./gradlew :app:detektCompareReport

bash ./gradlew :app:detekt
bash ./gradlew detektBaseline  # einmalig, um bestehenden Stand zu baselinen


bash ./gradlew buildSrc:build
bash ./gradlew :app:tasks --all --group=verification
bash ./gradlew :app:detekt
bash ./gradlew :app:diffVersionCatalogs
bash ./gradlew :app:detektCompareReport