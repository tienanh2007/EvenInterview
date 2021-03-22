# Even Coding Exercise: `evencache`

Imagine you've joined Even (welcome!) and are working on your first big project.
You've been tasked with implementing a new feature in our codebase.
In the process, you run into some bugs coming from code that you didn't write.
What do you do?

In this interview, we're interested in seeing your ability to both
**debug** and **modify** existing code.
Specifically, we'd like you to both **fix some bugs** and **implement a feature**
in this small but real-world codebase.

The docs below explain the code at a high level, and comments in the code explain the details.

## Background

This codebase implements a small **caching** library. Specifically, it provides:

- A generic **`Cache` interface** that abstracts away different cache backends,
  e.g. Redis vs. in-memory. It exposes methods like `get`, `set`, and `clear`.

- A **`RedisCache` implementation** of the `Cache` interface.
  This code is not included here, but a stub is shown for illustration.

- A **`MemoryCache` implementation** of the `Cache` interface using simple
  in-memory data structures (like dictionaries and linked lists).
  Supports capping memory usage with a `maxItems` parameter,
  and evicts the least recently read items when over capacity.

- A **`DedupingLoader` utility** for funneling _concurrent requests_ for a
  resource, to a _single call_ to actually load it.
  Callers differentiate resources by string `key`s, and pass a `load` function.
  If there's already a `load` in progress for a particular `key`,
  no additional `load` call is made; the existing `load` is awaited instead.
  `DedupingLoader` is _not_ meant to be a long-lived cache, so results are
  discarded once concurrent loads are finished.

- A **`ReadThroughCache` class** that ties the above into one friendly, easy-to-use API!
  This is the main API that application code is meant to use.
  Specifically, wraps a `Cache` and fronts it with a higher-level API that handles
  loading & caching data on cache misses.
  Guards against cache stampedes through both `DedupingLoader` as well as a
  probabilistic (random) algorithm for eager refresh before expiry.

All three classes have a few bugs, and `MemoryCache` doesn't yet implement support for expiring items.

## Instructions

1.  **Share your screen** if you're not already doing so. Thanks!

1.  **Run _one_ of the following** in your terminal, depending on your language:

    - Go:

      ```
      go test -run MemoryCache_Basic
      ```

    - JavaScript:

      ```
      npm install
      npm test -- --grep 'MemoryCache Basic'
      ```

    - Python:

      ```
      pip3 install -r requirements.txt
      python3 -m unittest memoryCache_test.TestMemoryCache.test_basic
      ```

    - Java:

      ```
      ./gradlew test --info --tests MemoryCacheTests.basic
      ```

    You should see a failing test!
    This is the first bug we'd like you to figure out & fix. 🐞

1.  **Read the rest of this readme** below and let us know if you have any questions
    about the format or logistics of this interview.

1.  When you're ready, go ahead fire up your editor and **start debugging**! 👩🏽‍💻

## Process

We'll let you know the next bug or feature after you fix or implement each one.

We'll decide on the fly which bugs & features to focus on,
e.g. based on your background, experience, and what we see.
As a result, we may not actually touch all the code in this repo.

We aim to make this interview doable within our allotted time, but if you need more time,
it’s no problem to finish afterward on your own and send us your code asynchronously.

Even though this is an interview, we want you to be as comfortable and "at home"
as you normally would be in the real world.
So e.g. feel free to **add print logs, Google, Stack Overflow**, etc.
to help you debug & implement.
And **you don't even need to "narrate your thoughts"** to us if you don't want to.
Consider us silent observers, like a usability study.

Finally, we're interested in seeing your ability to figure things out on your own,
but feel free to use us as a [rubber duck](https://en.wikipedia.org/wiki/Rubber_duck_debugging)
if you get stuck.
We may refrain from answering questions that could be answered through the code,
but we'll let you know if that's the case.

Let us know if you have any questions. Otherwise, let's do it! 🚀
