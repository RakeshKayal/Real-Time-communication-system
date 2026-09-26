package Com.test.Controller;

import Com.test.MessageModel_StructureModel.PersonalMsgDTO;
import Com.test.MessageModel_StructureModel.GroupMsgDTO;
import Com.test.Model.GroupChat;
import Com.test.Model.GroupMessage;
import Com.test.Model.Massege;
import Com.test.Model.user;

import Com.test.Repo.FriendRequestRepo;
import Com.test.Repo.GroupRepo;

import Com.test.Repo.userRegistrationRepo;
import Com.test.Service.GroupMessageService;
import Com.test.Service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller

public class messageController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GroupMessageService messageService;
    private final GroupRepo groupRepo;
    private final userRegistrationRepo userRepo;


    @Autowired private FriendRequestRepo friendRequestRepo;

//    @Autowired private MessageRepo messageRepo;
    @Autowired
    private MessageService personalService;

    @Autowired
    public messageController(SimpMessagingTemplate messagingTemplate,
                             GroupMessageService messageService,
                             GroupRepo   groupRepo,
                             userRegistrationRepo userRepo) {
        this.messagingTemplate = messagingTemplate;
        this.messageService = messageService;
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
    }

    @MessageMapping("/sendMessage")
    @Cacheable
    public void sendToGroup(GroupMsgDTO dto) {

        System.out.println("Received DTO: " + dto);
        // Fetch sender and group
        user sender = userRepo.findByName(dto.getSenderName());
        if (sender ==null){
            throw new RuntimeException("not found");
        }

        GroupChat group = groupRepo.findByGroupName(dto.getGroupName());
        if(group== null){
            throw  new RuntimeException("not found : "+ group);
        }


        // Save message to DB
        GroupMessage msg = new GroupMessage();
        msg.setSender(sender);
        msg.setGroup(group);
        msg.setContent(dto.getContent());
        msg.setSentAt(LocalDateTime.now());
        messageService.save(msg);

        System.out.println("Received message: " + dto.getContent());
        System.out.println("Saving to DB: " + msg);
        Map<String, Object> messageObj = new HashMap<>();
        messageObj.put("senderName", dto.getSenderName());
        messageObj.put("groupName", dto.getGroupName());
        messageObj.put("content", dto.getContent());
        messageObj.put("timestamp", LocalDateTime.now().toString());
        messageObj.put("type", "GROUP");

        messagingTemplate.convertAndSend("/topic/" + dto.getGroupName(), messageObj);
    }


    @MessageMapping("/private-message")
    public void sendPrivateMessage(PersonalMsgDTO dto) {
        user sender = userRepo.findByName(dto.getSenderName());
        user receiver = userRepo.findByName(dto.getReceiverName());

        if (sender == null && receiver == null) {
            throw new RuntimeException("not found sender and receiver");
        }

//        boolean isFriend = friendRequestRepo.findAcceptedRequestBetweenUsers(sender.getId(), receiver.getId()).isPresent();
//        if (!isFriend) {
//            throw new RuntimeException("Cannot send message. Friend request not accepted.");
//        }

        System.out.println("sender : "  +sender.getName());
        System.out.println("receiver : "+receiver.getName());
        Massege msg = new Massege();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setContent(dto.getContent());
        msg.setSentAt(LocalDateTime.now());
        personalService.save(msg);

        Map<String, Object> messageObj = new HashMap<>();
        messageObj.put("senderName", sender.getName());
        messageObj.put("receiverName", receiver.getName());
        messageObj.put("content", msg.getContent());
        messageObj.put("timestamp", msg.getSentAt().toString());
        messageObj.put("type", "PRIVATE");

        messagingTemplate.convertAndSendToUser(
                receiver.getName(),
                "/queue/messages",
                messageObj
        );



    }

    @MessageMapping("/notify")
    public void notifyUser(@Payload Map<String, Object> notification) {
        Object target = notification.get("to");

        if (target == null || target.toString().isBlank()) {
            throw new IllegalArgumentException("Notification recipient is required");
        }

        messagingTemplate.convertAndSendToUser(
                target.toString(),
                "/queue/notifications",
                notification
        );
    }
}
