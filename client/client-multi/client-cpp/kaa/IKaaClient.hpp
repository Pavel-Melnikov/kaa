/*
 * Copyright 2014-2015 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef IKAACLIENT_HPP_
#define IKAACLIENT_HPP_

#include "kaa/profile/IProfileContainer.hpp"
#include "kaa/notification/INotificationTopicListListener.hpp"
#include "kaa/notification/gen/NotificationDefinitions.hpp"
#include "kaa/notification/INotificationListener.hpp"
#include "kaa/configuration/storage/IConfigurationStorage.hpp"
#include "kaa/configuration/gen/ConfigurationDefinitions.hpp"
#include "kaa/event/registration/IAttachEndpointCallback.hpp"
#include "kaa/event/registration/IDetachEndpointCallback.hpp"
#include "kaa/event/registration/IUserAttachCallback.hpp"
#include "kaa/event/registration/IAttachStatusListener.hpp"
#include "kaa/event/IFetchEventListeners.hpp"
#include "kaa/log/ILogCollector.hpp"


namespace kaa {

class EventFamilyFactory;;
class IKaaChannelManager;
class IKaaDataMultiplexer;
class IKaaDataDemultiplexer;
class IConfigurationReceiver;
class KeyPair;

/**
 * Interface for the Kaa client.
 *
 * Base interface to operate with @link Kaa @endlink library.
 *
 * @author Yaroslav Zeygerman
 *
 */
class IKaaClient {
public:

    /**
     * Sets profile container implemented by the user.
     *
     * @param container User-defined container
     * @see AbstractProfileContainer
     *
     */
    virtual void setProfileContainer(ProfileContainerPtr container) = 0;

    /**
     * Retrieves Kaa event family factory.
     *
     * @return @link EventFamilyFactory @endlink object.
     *
     */
    virtual EventFamilyFactory& getEventFamilyFactory() = 0;

    /**
     * @brief Adds the listener which receives updates on the list of available topics.
     *
     * @param[in] listener    The listener which receives updates.
     * @see INotificationTopicListListener
     *
     */
    virtual void addTopicListListener(INotificationTopicListListener& listener) = 0;

    /**
     * @brief Removes listener which receives updates on the list of available topics.
     *
     * @param[in] listener    The listener which receives updates.
     * @see INotificationTopicListListener
     *
     */
    virtual void removeTopicListListener(INotificationTopicListListener& listener) = 0;

    /**
     * @brief Retrieves the list of available topics.
     *
     * @return The list of available topics.
     *
     */
    virtual Topics getTopics() = 0;

    /**
     * @brief Adds the listener which receives notifications on all available topics.
     *
     * @param[in] listener    The listener which receives notifications.
     *
     * @see INotificationListener
     */
    virtual void addNotificationListener(INotificationListener& listener) = 0;

    /**
     * @brief Adds the listener which receives notifications on the specified topic.
     *
     * Listener(s) for optional topics may be added/removed irrespective to whether subscription is already done or not.
     *
     * @param[in] topicId     The id of the topic (either mandatory or optional).
     * @param[in] listener    The listener which receives notifications.
     *
     * @throw UnavailableTopicException Throws if the unknown topic id is provided.
     *
     * @see INotificationListener
     */
    virtual void addNotificationListener(const std::string& topidId, INotificationListener& listener) = 0;

    /**
     * @brief Removes the listener which receives notifications on all available topics.
     *
     * @param[in] listener    The listener which receives notifications.
     *
     * @see INotificationListener
     */
    virtual void removeNotificationListener(INotificationListener& listener) = 0;

    /**
     * @brief Removes the listener which receives notifications on the specified topic.
     *
     * Listener(s) for optional topics may be added/removed irrespective to whether subscription is already done or not.
     *
     * @param[in] topicId     The id of topic (either mandatory or optional).
     * @param[in] listener    The listener which receives notifications.
     *
     * @throw UnavailableTopicException Throws if the unknown topic id is provided.
     *
     * @see INotificationListener
     */
    virtual void removeNotificationListener(const std::string& topidId, INotificationListener& listener) = 0;

    /**
     * @brief Subscribes to the specified optional topic to receive notifications on that topic.
     *
     * @param[in] topicId      The id of the optional topic.
     * @param[in] forceSync    Indicates whether the subscription request should be sent immediately to
     *                         the Operations server. If <i> false </i>, the request postpones to the explicit
     *                         call of @link sync() @endlink or to the first call of @link subscribeToTopic() @endlink,
     *                         @link subscribeToTopics() @endlink, @link unsubscribeFromTopic() @endlink or
     *                         @link unsubscribeFromTopics() @endlink with the <i> true </i> value for the
     *                         @link forceSync @endlink parameter.
     *
     * @throw UnavailableTopicException Throws if the unknown topic id is provided or the topic isn't optional.
     *
     * @see sync()
     */
    virtual void subscribeToTopic(const std::string& id, bool forceSync = true) = 0;

    /**
     * @brief Subscribes to the specified list of optional topics to receive notifications on those topics.
     *
     * @param[in] topicIds     The list of optional topic id-s.
     * @param[in] forceSync    Indicates whether the subscription request should be sent immediately to
     *                         the Operations server. If <i> false </i>, the request postpones to the explicit
     *                         call of @link sync() @endlink or to the first call of @link subscribeToTopic() @endlink,
     *                         @link subscribeToTopics() @endlink, @link unsubscribeFromTopic() @endlink or
     *                         @link unsubscribeFromTopics() @endlink with the <i> true </i> value for the
     *                         @link forceSync @endlink parameter.
     *
     * @throw UnavailableTopicException Throws if the unknown topic id is provided or the topic isn't optional.
     *
     * @see sync()
     */
    virtual void subscribeToTopics(const std::list<std::string>& idList, bool forceSync = true) = 0;

    /**
     * @brief Unsubscribes from the specified optional topic to stop receiving notifications on that topic.
     *
     * @param[in] topicId      The id of the optional topic.
     * @param[in] forceSync    Indicates whether the subscription request should be sent immediately to
     *                         the Operations server or postponed. If <i> false </i>, the request is postponed either
     *                         to the explicit
     *                         call of @link sync() @endlink or to the first call of one of the following functions
     *                         with the @link forceSync @endlink parameter set to <i> true </i>:
     *                         @link subscribeToTopic() @endlink, @link subscribeToTopics() @endlink,
     *                         @link unsubscribeFromTopic() @endlink or @link unsubscribeFromTopics() @endlink .
     *
     * @throw UnavailableTopicException Throws if the unknown topic id is provided or the topic isn't optional.
     *
     * @see sync()
     */
    virtual void unsubscribeFromTopic(const std::string& id, bool forceSync = true) = 0;

    /**
     * @brief Unsubscribes from the specified list of optional topics to stop receiving notifications on those topics.
     *
     * @param[in] topicId      The list of optional topic id-s.
     * @param[in] forceSync    Indicates whether the subscription request should be sent immediately to
     *                         the Operations server or postponed. If <i> false </i>, the request is postponed either
     *                         to the explicit
     *                         call of @link sync() @endlink or to the first call of one of the following functions
     *                         with the @link forceSync @endlink parameter set to <i> true </i>:
     *                         @link subscribeToTopic() @endlink, @link subscribeToTopics() @endlink,
     *                         @link unsubscribeFromTopic() @endlink or @link unsubscribeFromTopics() @endlink .
     *
     * @throw UnavailableTopicException Throws if the unknown topic id is provided or the topic isn't optional.
     *
     * @see sync()
     */
    virtual void unsubscribeFromTopics(const std::list<std::string>& idList, bool forceSync = true) = 0;

    /**
     * @brief Sends subscription request(s) to the Operations server.
     *
     * Use as a convenient way to send several subscription requests at once.
     * @code
     * IKaaClient& kaaClient = Kaa::getKaaClient();
     *
     * // Add listener(s) to receive notifications on topic(s)
     *
     * kaaClient.subscribeToTopics({"optional_topic1_id", "optional_topic2_id"}, false);
     * kaaClient.unsubscribeFromTopic("optional_topic3_id", false);
     *
     * kaaClient.syncTopicSubscriptions();
     * @endcode
     */
    virtual void syncTopicSubscriptions() = 0;

    /**
     * Subscribes listener of configuration updates.
     *
     * @param receiver Listener to be added to notification list.
     */
    virtual void addConfigurationListener(IConfigurationReceiver &receiver) = 0;

    /**
     * Unsubscribes listener of configuration updates.
     *
     * @param receiver Listener to be removed from notification list.
     */
    virtual void removeConfigurationListener(IConfigurationReceiver &receiver) = 0;
    /**
     * Returns full configuration tree which is actual at current moment.
     *
     * @return @link ICommonRecord @endlink containing current configuration tree.
     */
    virtual const KaaRootConfiguration& getConfiguration() = 0;

    /**
     * Registers new configuration persistence routines. Replaces previously set value.
     * Memory pointed by given parameter should be managed by user.
     *
     * @param storage User-defined persistence routines.
     * @see IConfigurationStorage
     */
    virtual void setConfigurationStorage(IConfigurationStoragePtr storage) = 0;

    /**
     * @brief Attaches the specified endpoint to the user to which the current endpoint is attached.
     *
     *    @param[in] endpointAccessToken    The access token of the endpoint to be attached to the user.
     * @param[in] listener               The optional listener to notify of the result.
     *
     * @throw BadCredentials                The endpoint access token is empty.
     * @throw TransportNotFoundException    The Kaa SDK isn't fully initialized.
     * @throw KaaException                  Some other failure has happened.
     */
    virtual void attachEndpoint(const std::string&  endpointAccessToken
                               , IAttachEndpointCallbackPtr listener = IAttachEndpointCallbackPtr()) = 0;

    /**
     * @brief Detaches the specified endpoint from the user to which the current endpoint is attached.
     *
     * @param[in] endpointKeyHash    The key hash of the endpoint to be detached from the user.
     * @param[in] listener           The optional listener to notify of the result.
     *
     * @throw BadCredentials                The endpoint access token is empty.
     * @throw TransportNotFoundException    The Kaa SDK isn't fully initialized.
     * @throw KaaException                  Some other failure has happened.
     */
    virtual void detachEndpoint(const std::string&  endpointKeyHash
                               , IDetachEndpointCallbackPtr listener = IDetachEndpointCallbackPtr()) = 0;

    /**
     * @brief Attaches the current endpoint to the specifier user. The user verification is carried out by the default verifier.
     *
     * @b NOTE: If the default user verifier (@link DEFAULT_USER_VERIFIER_TOKEN @endlink) is not specified,
     * the attach attempt fails with the @c KaaException exception.
     *
     * <b>Only endpoints associated with the same user can exchange events.</b>
     *
     * @param[in] userExternalId     The external user ID.
     * @param[in] userAccessToken    The user access token.
     *
     * @throw BadCredentials                The endpoint access token is empty.
     * @throw TransportNotFoundException    The Kaa SDK isn't fully initialized.
     * @throw KaaException                  Some other failure has happened.
     */
    virtual void attachUser(const std::string& userExternalId, const std::string& userAccessToken
                           , IUserAttachCallbackPtr listener = IUserAttachCallbackPtr()) = 0;

    virtual void attachUser(const std::string& userExternalId, const std::string& userAccessToken
                           , const std::string& userVerifierToken, IUserAttachCallbackPtr listener = IUserAttachCallbackPtr()) = 0;

    /**
     * @brief Sets listener to notify of the current endpoint is attached/detached by another one.
     *
     * @param[in] listener    Listener to notify of the attach status is changed.
     */
    virtual void setAttachStatusListener(IAttachStatusListenerPtr listener) = 0;
    /**
     * @brief Checks if the current endpoint is already attached to some user.
     *
     * @return TRUE if the current endpoint is attached, FALSE otherwise.
     */
    virtual bool isAttachedToUser() = 0;

    /**
     * Submits an event listeners resolution request
     *
     * @param eventFQNs     List of event class FQNs which have to be supported by endpoint.
     * @param listener      Result listener {@link IFetchEventListeners}}
     *
     * @throw KaaException when data is invalid (empty list or null listener)
     *
     * @return Request ID of submitted request
     */
    virtual std::int32_t findEventListeners(const std::list<std::string>& eventFQNs, IFetchEventListenersPtr listener) = 0;

    /**
     * @brief Adds a new log record to the log storage.
     *
     * To store log records, @c MemoryLogStorage is used by default. Use @link setStorage() @endlink to set
     * your own implementation.
     *
     * @param[in] record    The log record to be added.
     *
     * @see KaaUserLogRecord
     * @see ILogStorage
     */
    virtual void addLogRecord(const KaaUserLogRecord& record) = 0;

    /**
     * @brief Sets the new log storage.
     *
     * @c MemoryLogStorage is used by default.
     *
     * @param[in] storage    The @c ILogStorage implementation.
     *
     * @throw KaaException    The storage is NULL.
     */
    virtual void setLogStorage(ILogStoragePtr storage) = 0;

    /**
     * @brief Sets the new log upload strategy.
     *
     * @c DefaultLogUploadStrategy is used by default.
     *
     * @param[in] strategy    The @c ILogUploadStrategy implementation.
     *
     * @throw KaaException    The strategy is NULL.
     */
    virtual void setLogUploadStrategy(ILogUploadStrategyPtr strategy) = 0;

    /**
     * Retrieves the Channel Manager
     */
    virtual IKaaChannelManager&                 getChannelManager() = 0;
    /**
     * Retrieves the client's public and private key.
     *
     * Required in user implementation of an operation data channel.
     * Public key hash (SHA-1) is used by servers as identification number to
     * uniquely identify each connected endpoint.
     *
     * Private key is used by encryption schema between endpoint and servers.
     *
     * @return client's public/private key pair
     */
    virtual const KeyPair&                    getClientKeyPair() = 0;

    /**
     * Retrieves Kaa operations data multiplexer
     *
     * @return @link IKaaDataMultiplexer @endlink object
     */
    virtual IKaaDataMultiplexer&              getOperationMultiplexer() = 0;

    /**
     * Retrieves Kaa operations data demultiplexer
     *
     * @return @link IKaaDataDemultiplexer @endlink object
     */
    virtual IKaaDataDemultiplexer&            getOperationDemultiplexer() = 0;

    /**
     * Retrieves Kaa bootstrap data multiplexer
     *
     * @return @link IKaaDataMultiplexer @endlink object
     */
    virtual IKaaDataMultiplexer&              getBootstrapMultiplexer() = 0;

    /**
     * Retrieves Kaa bootstrap data demultiplexer
     *
     * @return @link IKaaDataDemultiplexer @endlink object
     */
    virtual IKaaDataDemultiplexer&            getBootstrapDemultiplexer() = 0;

    virtual ~IKaaClient() { }
};

}


#endif /* IKAACLIENT_HPP_ */
