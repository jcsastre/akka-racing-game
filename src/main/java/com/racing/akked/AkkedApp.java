package com.racing.akked;

import akka.actor.typed.ActorSystem;

public class AkkedApp {

    public static void main(String[] args) {

        final ActorSystem<RaceController.Command> bigPrimesSystem =
                ActorSystem.create(RaceController.create(), "RaceSimulation");

        bigPrimesSystem.tell(new RaceController.StartCommand());
    }
}
