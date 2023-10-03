# POM "mocuishle-legacy"

After years of using *Mo Cuishle* - A caching proxy for offline use - developed 2013 - 2016 I'm going *Open Source* with it.

This module *Mo Cuishle - Legacy* is **no**t intended to be a base of **further development** but to migrate its features to more sophisticated implementations. 

The modules of *Mo Cuishle* will be developed in the parent module. See the **[README](../README.md)** and **https://ganskef.github.io/MoCuishle/** for further information.

## Test Coverage

The test coverage is 28% only after replacing LittleProxy-mitm with OkProxy but I've used it over the last 10 years on a daily base.

    mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent test
    mvn org.jacoco:jacoco-maven-plugin:report

A better unit test coverage is an important goal of new implemented modules. Implementing JUnit tests in *Mo Cuishle - Legacy* is an excellent starting point of course.