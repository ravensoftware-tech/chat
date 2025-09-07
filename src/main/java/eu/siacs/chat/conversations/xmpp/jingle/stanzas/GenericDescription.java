package tech.ravensoftware.chat.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import tech.ravensoftware.chat.xml.Element;

public class GenericDescription extends Element {

    GenericDescription(String name, final String namespace) {
        super(name, namespace);
        Preconditions.checkArgument("description".equals(name));
    }

    public static GenericDescription upgrade(final Element element) {
        Preconditions.checkArgument("description".equals(element.getName()));
        final GenericDescription description = new GenericDescription("description", element.getNamespace());
        description.setAttributes(element.getAttributes());
        description.setChildren(element.getChildren());
        return description;
    }
}
