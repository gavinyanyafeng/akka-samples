/**
 * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
 */

package sample.persistence;

//#persistent-actor-example

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotOffer;

import java.io.Serializable;
import java.util.ArrayList;

import static java.util.Arrays.asList;

class Cmd implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String data;

    public Cmd(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}

class Evt implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String data;

    public Evt(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}

class ExampleState implements Serializable {
    private static final long serialVersionUID = 1L;
    private final ArrayList<String> events;

    public ExampleState() {
        this(new ArrayList<>());
    }

    public ExampleState(ArrayList<String> events) {
        this.events = events;
    }

    public ExampleState copy() {
        return new ExampleState(new ArrayList<>(events));
    }

    public void update(Evt evt) {
        events.add(evt.getData());
    }

    public int size() {
        return events.size();
    }

    @Override
    public String toString() {
        return events.toString();
    }
}

class ExamplePersistentActor extends AbstractPersistentActor {

    private ExampleState state = new ExampleState();

    public int getNumEvents() {
        return state.size();
    }

    @Override
    public String persistenceId() { return "sample-id-1"; }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
            .match(Evt.class, e -> {
                state.update(e);
                System.out.println("Recover from event " + e.getData());
            })
            .match(SnapshotOffer.class, ss -> {
                state = (ExampleState) ss.snapshot();
                System.out.println("Recover from snapshot " + ss);
            })
            .match(RecoveryCompleted.class, rc -> {
                System.out.println("Recovery complete. state = " + state);
            })
            .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Cmd.class, c -> {
                final String data = c.getData();
                final Evt evt1 = new Evt(data + "-" + getNumEvents());
                final Evt evt2 = new Evt(data + "-" + (getNumEvents() + 1));
                persistAll(asList(evt1, evt2), (Evt evt) -> {
                    state.update(evt);
                    if (evt.equals(evt2)) {
                        getContext().system().eventStream().publish(evt);
                    }
                });
            })
            .matchEquals("snap", s -> saveSnapshot(state.copy()))
            .matchEquals("print", s -> System.out.println(state))
            .build();
    }

}
//#persistent-actor-example

public class PersistentActorExample {
    public static void main(String... args) throws Exception {
        final ActorSystem system = ActorSystem.create("example");
        final ActorRef persistentActor = system.actorOf(Props.create(ExamplePersistentActor.class), "persistentActor-4-java8");
        persistentActor.tell(new Cmd("foo"), null);
        persistentActor.tell(new Cmd("baz"), null);
        persistentActor.tell(new Cmd("bar"), null);
        persistentActor.tell("snap", null);
        persistentActor.tell(new Cmd("buzz"), null);
        persistentActor.tell("print", null);

        Thread.sleep(10000);
        system.terminate();
    }
}
