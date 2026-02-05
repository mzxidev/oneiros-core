#!/bin/bash
cd /home/marcel/IdeaProjects/oneiros-core
java -cp "build/classes/java/main:$(find ~/.gradle/caches -name 'reactor-core*.jar' | head -1):$(find ~/.gradle/caches -name 'reactive-streams*.jar' | head -1)" io.oneiros.transaction.FluentTransactionTest
