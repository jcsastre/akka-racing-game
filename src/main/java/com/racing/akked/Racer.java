package com.racing.akked;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Random;

public class Racer extends AbstractBehavior<Racer.Command> {

    static final Logger logger = LoggerFactory.getLogger(Racer.class);

    public interface Command extends Serializable {};

    @Value
    public static class StartCommand implements Command {
        public static final long serialVersionUID = 1L;
        int raceLength;
    }

    @Value
    public static class PositionCommand implements Command {
        public static final long serialVersionUID = 1L;
        ActorRef<RaceController.Command> controller;
    }

    private int raceLength;
    private final double defaultAverageSpeed = 48.2;
    private int averageSpeedAdjustmentFactor;
    private Random random;
    private double currentSpeed = 0;
    private double currentPosition = 0;

    private Racer(ActorContext<Racer.Command> context) {
        super(context);
    }

    public static Behavior<Racer.Command> create() {
        return Behaviors.setup(Racer::new);
    }

    private double getMaxSpeed() {
        return defaultAverageSpeed * (1+((double)averageSpeedAdjustmentFactor / 100));
    }

    private double getDistanceMovedPerSecond() {
        return currentSpeed * 1000 / 3600;
    }

    private void determineNextSpeed() {
        if (currentPosition < (raceLength / 4)) {
            currentSpeed = currentSpeed  + (((getMaxSpeed() - currentSpeed) / 10) * random.nextDouble());
        }
        else {
            currentSpeed = currentSpeed * (0.5 + random.nextDouble());
        }

        if (currentSpeed > getMaxSpeed())
            currentSpeed = getMaxSpeed();

        if (currentSpeed < 5)
            currentSpeed = 5;

        if (currentPosition > (raceLength / 2) && currentSpeed < getMaxSpeed() / 2) {
            currentSpeed = getMaxSpeed() / 2;
        }
    }

    @Override
    public Receive<Racer.Command> createReceive() {
        return newReceiveBuilder()

                .onMessage(StartCommand.class, command -> {

                    this.raceLength = command.getRaceLength();
                    this.random = new Random();
                    this.averageSpeedAdjustmentFactor = random.nextInt(30) - 10;

                    return this;
                })

                .onMessage(PositionCommand.class, command -> {

                    determineNextSpeed();
                    currentPosition += getDistanceMovedPerSecond();
                    if (currentPosition > raceLength )
                        currentPosition  = raceLength;

                    //TODO: tell controller current position
                    final RaceController.RacerUpdateCommand racerUpdateCommand =
                            new RaceController.RacerUpdateCommand(getContext().getSelf(), (int) currentPosition);
                    command.getController().tell(racerUpdateCommand);

                    return this;
                })

                .build();
    }
}
