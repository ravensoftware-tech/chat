package tech.ravensoftware.chat.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import tech.ravensoftware.chat.xml.Element;
import tech.ravensoftware.chat.xml.Namespace;

public class Proceed extends Element {
    private Proceed() {
        super("propose", Namespace.JINGLE_MESSAGE);
    }

    public static Proceed upgrade(final Element element) {
        Preconditions.checkArgument("proceed".equals(element.getName()));
        Preconditions.checkArgument(Namespace.JINGLE_MESSAGE.equals(element.getNamespace()));
        final Proceed propose = new Proceed();
        propose.setAttributes(element.getAttributes());
        propose.setChildren(element.getChildren());
        return propose;
    }

    public Integer getDeviceId() {
        final Element device = this.findChild("device");
        final String id = device == null ? null : device.getAttribute("id");
        if (id == null) {
            return null;
        }
        return Ints.tryParse(id);
    }
}
