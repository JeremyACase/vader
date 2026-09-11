package org.vader.common.model.vader.queue;

/**
 * A snapshot of the back pressure on an inbox/outbox queue for a single model type: what is
 * waiting to be consumed ({@link Ingress}) and how much consumer capacity remains
 * ({@link Egress}).
 */
public class BackPressure {

    private Ingress ingress;

    private Egress egress;

    public Ingress getIngress() {
        return this.ingress;
    }

    public void setIngress(Ingress ingress) {
        this.ingress = ingress;
    }

    public Egress getEgress() {
        return this.egress;
    }

    public void setEgress(Egress egress) {
        this.egress = egress;
    }
}
