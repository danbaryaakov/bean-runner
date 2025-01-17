/*
 * This file is part of bean-runner.
 *
 * Copyright (C) 2025 Dan Bar-Yaakov
 *
 * bean-runner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bean-runner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.beanrunner.examples.rewind;

import java.util.Random;

public class RandomPersonGenerator {

    // List of sample names to randomly select from
    private static final String[] NAMES = {
        "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Hank", "Ivy", "Jack"
    };

    private static final Random RANDOM = new Random();

    // Generate a random Person instance
    public static Person generateRandomPerson() {
        String name = NAMES[RANDOM.nextInt(NAMES.length)]; // Randomly select a name
        int age = RANDOM.nextInt(100) + 1; // Generate random age between 1 and 100
        return new Person(name, age);
    }

    public static void main(String[] args) {
        // Example usage: generate and print 5 random Person instances
        for (int i = 0; i < 5; i++) {
            Person randomPerson = generateRandomPerson();
            System.out.println(randomPerson);
        }
    }
}