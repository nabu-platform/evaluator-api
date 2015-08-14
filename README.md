# Evaluator

This package contains an evaluator that has an xpath-like syntax.

## Context

Take these classes as example:

```java
class MyObject {
	String myField, myVar;
	List<MyObject> myList;
}
```

Suppose you perform this query:

```java
myObject/myList[myField = myVar]
```

This will return all instances of `MyObject` in the variable `myList` where the string `myField` matches the value of the string `myVar`.
Both `myField` and `myVar` will be located in the context of the `MyObject` instances that are in the list.

**However** if the value of either variable is "null" in the list context for a specific instance, there are two booleans that will trigger other behavior:

- **allowParentLookup** (default **false**): this will search **all parents** (going up the stack) to find the variable. Use with caution, naming collisions are likely in moderately complex scenarios
- **allowRootLookup** (default **false**): this will search for the variable at the root level, this should drastically reduce the chance for naming collisions

In the default scenario you can still access all parent contexts by using subsequent ".." to browse upwards:

```java
myObject/myList[myField = ../myVar]
```

Alternatively you can directly address the root context by using an absolute path:

```java
myObject/myList[myField = /myVar]
```
