## Null-safe Java expressions
[![Build Status](https://travis-ci.org/HBTGmbH/java-nullsafe.svg?branch=master)](https://travis-ci.org/HBTGmbH/java-nullsafe)

A [Java Agent](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/package-summary.html) for null-safe Java expressions.

### How to use

Via runtime class transformation by running the JVM with the JVM argument `-javaagent:nullsafe-1.0.0-SNAPSHOT.jar`.

Or, via build-time transformation by running `java -jar nullsafe-1.0.0-SNAPSHOT.jar yourapp.jar yourapp-transformed.jar` once after your application build to transform the classes offline.

```Java
@lombok.Data class Car {
  Model model;
}
@lombok.Data class Model {
  boolean suv;
  String name;
}

import static de.hbt.nullsafe.Nullsafe.__nullsafe;

Car nullCar = ...; // <- any expression producing null
// The following will NOT throw an NPE:
assertNull(__nullsafe(nullCar.getModel().getName()));
```
