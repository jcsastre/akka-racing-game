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

    private final double defaultAverageSpeed = 48.2;
    private int averageSpeedAdjustmentFactor;
    private Random random;
    private double currentSpeed = 0;

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

    private void determineNextSpeed(int raceLength, double currentPosition) {
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
        return notStartedYet();
    }

    public Receive<Racer.Command> notStartedYet() {

        return newReceiveBuilder()

                .onMessage(StartCommand.class, command -> {

                    this.random = new Random();
                    this.averageSpeedAdjustmentFactor = random.nextInt(30) - 10;

                    return running(command.getRaceLength(), 0);
                })

                .onMessage(PositionCommand.class, command -> {

                    final RaceController.RacerUpdateCommand racerUpdateCommand =
                            new RaceController.RacerUpdateCommand(getContext().getSelf(), 0);
                    command.getController().tell(racerUpdateCommand);

                    return Behaviors.same();
                })

                .build();
    }

    public Receive<Racer.Command> running(int raceLength, double currentPosition) {

        return newReceiveBuilder()

                .onMessage(PositionCommand.class, command -> {

                    determineNextSpeed(raceLength, currentPosition);

                    double newPosition = currentPosition + getDistanceMovedPerSecond();
                    if (newPosition > raceLength) {
                        newPosition = raceLength;
                    }

                    final RaceController.RacerUpdateCommand racerUpdateCommand =
                            new RaceController.RacerUpdateCommand(getContext().getSelf(), (int) newPosition);
                    command.getController().tell(racerUpdateCommand);

                    if (newPosition == raceLength ) {
                        return completed(raceLength);
                    } else {
                        return running(raceLength, newPosition);
                    }
                })

                .build();
    }

    public Receive<Racer.Command> completed(double raceLength) {
        return newReceiveBuilder()

                .onMessage(PositionCommand.class, command -> {

                    final RaceController.RacerUpdateCommand racerUpdateCommand =
                            new RaceController.RacerUpdateCommand(getContext().getSelf(), (int) raceLength);
                    command.getController().tell(racerUpdateCommand);

                    return Behaviors.same();
                })

                .build();
    }
}
