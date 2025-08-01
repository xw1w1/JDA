/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.api.interactions.callbacks;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.tree.ComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.dv8tion.jda.internal.requests.restaction.interactions.MessageEditCallbackActionImpl;
import net.dv8tion.jda.internal.utils.Checks;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

/**
 * Interactions which allow a target message to be edited on use.
 *
 * <p>Editing a message using these methods will automatically acknowledge the interaction.
 *
 * <p><b>Deferred Edits</b><br>
 *
 * Similar to {@link IReplyCallback}, message edits can be deferred and performed later with {@link #deferEdit()}.
 * A deferred edit tells Discord, that you intend to edit the message this interaction was performed on, but will do so later.
 * However, you can defer the edit and never do it, which is effectively a no-operation acknowledgement of the interaction.
 *
 * <p>If an edit is {@link #deferEdit() deferred}, it becomes the <b>original</b> message of the interaction hook.
 * This means all the methods with {@code original} in the name, such as {@link InteractionHook#editOriginal(String)},
 * will affect that original message you edited.
 */
public interface IMessageEditCallback extends IDeferrableCallback
{
    /**
     * No-op acknowledgement of this interaction.
     * <br>This tells discord you intend to update the message that the triggering component is a part of using the {@link #getHook() InteractionHook} instead of sending a reply message.
     * You are not required to actually update the message, this will simply acknowledge that you accepted the interaction.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     * <br>When the acknowledgement is sent after the interaction expired, you will receive {@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION ErrorResponse.UNKNOWN_INTERACTION}.
     * <p>Use {@link #editMessage(String)} to edit it directly.
     *
     * @return {@link MessageEditCallbackAction} that can be used to update the message
     *
     * @see    #editMessage(String)
     */
    @Nonnull
    @CheckReturnValue
    MessageEditCallbackAction deferEdit();

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  message
     *         The new message content to use
     *
     * @throws IllegalArgumentException
     *         If the provided message is null
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editMessage(@Nonnull MessageEditData message)
    {
        Checks.notNull(message, "Message");
        MessageEditCallbackActionImpl action = (MessageEditCallbackActionImpl) deferEdit();
        return action.applyData(message);
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  content
     *         The new message content to use
     *
     * @throws IllegalArgumentException
     *         If the provided content is null or longer than {@value Message#MAX_CONTENT_LENGTH} characters
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editMessage(@Nonnull String content)
    {
        Checks.notNull(content, "Content");
        return deferEdit().setContent(content);
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  components
     *         The {@link MessageTopLevelComponent MessageTopLevelComponents} to set, can be empty to remove components,
     *         can contain up to {@value Message#MAX_COMPONENT_COUNT} V1 components.
     *         There are no limits for {@linkplain MessageRequest#isUsingComponentsV2() V2 components}
     *         outside the {@linkplain Message#MAX_COMPONENT_COUNT_IN_COMPONENT_TREE total tree size} ({@value Message#MAX_COMPONENT_COUNT_IN_COMPONENT_TREE}).
     *
     * @throws IllegalArgumentException
     *         <ul>
     *             <li>If {@code null} is provided</li>
     *             <li>If any of the provided components are not {@linkplain Component.Type#isMessageCompatible() compatible with messages}</li>
     *         </ul>
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     *
     * @see    MessageTopLevelComponent#isMessageCompatible()
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editComponents(@Nonnull Collection<? extends MessageTopLevelComponent> components)
    {
        Checks.noneNull(components, "Components");
        return deferEdit().setComponents(components);
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  components
     *         The {@link MessageTopLevelComponent MessageTopLevelComponents} to set, can be empty to remove components,
     *         can contain up to {@value Message#MAX_COMPONENT_COUNT} V1 components.
     *         There are no limits for {@linkplain MessageRequest#isUsingComponentsV2() V2 components}
     *         outside the {@linkplain Message#MAX_COMPONENT_COUNT_IN_COMPONENT_TREE total tree size} ({@value Message#MAX_COMPONENT_COUNT_IN_COMPONENT_TREE}).
     *
     * @throws IllegalArgumentException
     *         <ul>
     *             <li>If {@code null} is provided</li>
     *             <li>If any of the provided components are not {@linkplain Component.Type#isMessageCompatible() compatible with messages}</li>
     *         </ul>
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     *
     * @see    MessageTopLevelComponent#isMessageCompatible()
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editComponents(@Nonnull MessageTopLevelComponent... components)
    {
        Checks.noneNull(components, "components");
        return editComponents(Arrays.asList(components));
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  tree
     *         The new {@link ComponentTree} to set, can be empty to remove components,
     *         containing up to {@value Message#MAX_COMPONENT_COUNT} V1 components.
     *         There are no limits for {@linkplain MessageRequest#isUsingComponentsV2() V2 components}
     *         outside the {@linkplain Message#MAX_COMPONENT_COUNT_IN_COMPONENT_TREE total tree size} ({@value Message#MAX_COMPONENT_COUNT_IN_COMPONENT_TREE}).
     *
     * @throws IllegalArgumentException
     *         <ul>
     *             <li>If {@code null} is provided</li>
     *             <li>If any of the provided components are not {@linkplain Component.Type#isMessageCompatible() compatible with messages}</li>
     *             <li>When using components V1, if more than {@value Message#MAX_COMPONENT_COUNT} components are provided</li>
     *         </ul>
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     *
     * @see    MessageTopLevelComponent#isMessageCompatible()
     * @see    net.dv8tion.jda.api.components.tree.MessageComponentTree MessageComponentTree
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editComponents(@Nonnull ComponentTree<? extends MessageTopLevelComponent> tree)
    {
        Checks.notNull(tree, "ComponentTree");
        return editComponents(tree.getComponents());
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  embeds
     *         The new {@link MessageEmbed MessageEmbeds}
     *
     * @throws IllegalArgumentException
     *         If null or more than {@value Message#MAX_EMBED_COUNT} embeds are provided
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editMessageEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds)
    {
        Checks.noneNull(embeds, "MessageEmbed");
        return deferEdit().setEmbeds(embeds);
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  embeds
     *         The new message embeds to include in the message
     *
     * @throws IllegalArgumentException
     *         If null or more than {@value Message#MAX_EMBED_COUNT} embeds are provided
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editMessageEmbeds(@Nonnull MessageEmbed... embeds)
    {
        Checks.noneNull(embeds, "MessageEmbed");
        return deferEdit().setEmbeds(embeds);
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  format
     *         The format string for the new message content
     * @param  args
     *         The format arguments
     *
     * @throws IllegalArgumentException
     *         If the provided format is null
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editMessageFormat(@Nonnull String format, @Nonnull Object... args)
    {
        Checks.notNull(format, "Format String");
        return editMessage(String.format(format, args));
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p><b>Resource Handling Note:</b> Once the request is handed off to the requester, for example when you call {@link RestAction#queue()},
     * the requester will automatically clean up all opened files by itself. You are only responsible to close them yourself if it is never handed off properly.
     * For instance, if an exception occurs after using {@link FileUpload#fromData(File)}, before calling {@link RestAction#queue()}.
     * You can safely use a try-with-resources to handle this, since {@link FileUpload#close()} becomes ineffective once the request is handed off.
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  attachments
     *         The new attachments of the message (Can be {@link FileUpload FileUploads} or {@link net.dv8tion.jda.api.utils.AttachmentUpdate AttachmentUpdates})
     *
     * @throws IllegalArgumentException
     *         If null is provided
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     *
     * @see    AttachedFile#fromAttachment(Message.Attachment)
     * @see    FileUpload#fromData(InputStream, String)
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editMessageAttachments(@Nonnull Collection<? extends AttachedFile> attachments)
    {
        Checks.noneNull(attachments, "Attachments");
        return deferEdit().setAttachments(attachments);
    }

    /**
     * Acknowledgement of this interaction with a message update.
     * <br>You can use {@link #getHook()} to edit the message further.
     *
     * <p><b>You can only use deferEdit() or editMessage() once per interaction!</b> Use {@link #getHook()} for any additional updates.
     *
     * <p><b>You only have 3 seconds to acknowledge an interaction!</b>
     *
     * <p><b>Resource Handling Note:</b> Once the request is handed off to the requester, for example when you call {@link RestAction#queue()},
     * the requester will automatically clean up all opened files by itself. You are only responsible to close them yourself if it is never handed off properly.
     * For instance, if an exception occurs after using {@link FileUpload#fromData(File)}, before calling {@link RestAction#queue()}.
     * You can safely use a try-with-resources to handle this, since {@link FileUpload#close()} becomes ineffective once the request is handed off.
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INTERACTION UNKNOWN_INTERACTION}
     *     <br>When the acknowledgement is sent after the interaction expired.</li>
     *
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#INVALID_FORM_BODY INVALID_FORM_BODY}
     *     <br>{@linkplain MessageRequest#useComponentsV2(boolean) Components V2} is used by the to-be-edited message, and this request has non-empty content or embeds.</li>
     * </ul>
     *
     * @param  attachments
     *         The new attachments of the message (Can be {@link FileUpload FileUploads} or {@link net.dv8tion.jda.api.utils.AttachmentUpdate AttachmentUpdates})
     *
     * @throws IllegalArgumentException
     *         If null is provided
     *
     * @return {@link MessageEditCallbackAction} that can be used to further update the message
     *
     * @see    AttachedFile#fromAttachment(Message.Attachment)
     * @see    FileUpload#fromData(InputStream, String)
     */
    @Nonnull
    @CheckReturnValue
    default MessageEditCallbackAction editMessageAttachments(@Nonnull AttachedFile... attachments)
    {
        Checks.noneNull(attachments, "Attachments");
        return deferEdit().setAttachments(attachments);
    }
}
