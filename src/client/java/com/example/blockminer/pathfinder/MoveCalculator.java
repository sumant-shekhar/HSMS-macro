package com.example.blockminer.pathfinder;

@FunctionalInterface
public interface MoveCalculator {
    void calculate(Moves move, int parentX, int parentY, int parentZ, MovementResult result);
}
