package com.example.blockminer.pathfinder;

public interface IGoal {
    boolean isAtGoal(int x, int y, int z);

    double heuristic(int x, int y, int z);
}
