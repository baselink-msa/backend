package com.baseball.waiting_room_service.service;

final class DbPressurePolicy {

    private DbPressurePolicy() {
    }

    static Result evaluate(int connectionCount, int budget, int caution, int warning, int critical) {
        int safeBudget = Math.max(1, budget);
        int safeConnectionCount = Math.max(0, connectionCount);

        if (safeConnectionCount >= safeBudget) {
            return new Result(safeConnectionCount, safeBudget, 0, "STOP");
        }
        if (safeConnectionCount >= Math.max(0, critical)) {
            return new Result(safeConnectionCount, safeBudget, 25, "CRITICAL");
        }
        if (safeConnectionCount >= Math.max(0, warning)) {
            return new Result(safeConnectionCount, safeBudget, 50, "WARNING");
        }
        if (safeConnectionCount >= Math.max(0, caution)) {
            return new Result(safeConnectionCount, safeBudget, 75, "CAUTION");
        }
        return new Result(safeConnectionCount, safeBudget, 100, "NORMAL");
    }

    record Result(
            int connectionCount,
            int budget,
            int throttlePercent,
            String level) {
    }
}
